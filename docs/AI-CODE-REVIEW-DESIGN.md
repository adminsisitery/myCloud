# AI 代码评审系统 —— 设计文档

> 基于 LangGraph + Python 3.12，挂载到 GitHub Actions CI 流水线。
> 核心思想：确定性规则引擎处理已知场景，LLM Agent 处理需要上下文的场景，两者结果合并输出。

## 1. 总体架构

```
CI 触发（git push / PR）
  │
  ▼
┌─────────────────────────────────────────────────┐
│                 Orchestrator                      │
│  (LangGraph StateGraph — 状态驱动流程控制)          │
│                                                   │
│  ┌──────────┐  ┌──────────┐  ┌───────────────┐  │
│  │ ① Parse  │→│ ② Rules  │→│ ③ AI Review   │  │
│  │   Diff   │  │  Engine  │  │   (LLM)       │  │
│  └──────────┘  └──────────┘  └───────┬───────┘  │
│                                       │          │
│                          ┌────────────▼────────┐ │
│                          │ ④ Aggregate & Rank  │ │
│                          └────────┬────────────┘ │
│                                   │              │
│                          ┌────────▼────────┐     │
│                          │ ⑤ Output / Fix   │     │
│                          └─────────────────┘     │
└─────────────────────────────────────────────────┘
  │
  ▼
输出: GitHub PR Comment / JSON Report / Auto-Fix PR
```

## 2. LangGraph 状态机设计

```python
from typing_extensions import TypedDict, Literal
from langgraph.graph import StateGraph, END

class ReviewState(TypedDict):
    # 输入
    repo_path: str
    base_branch: str          # "main"
    target_branch: str        # "feature/xxx"
    model: str                # "deepseek-chat"
    rules_config: str         # 规则配置 YAML 路径

    # 中间状态
    changed_files: list[dict] # [{path, diff, added_lines, deleted_lines}]
    rule_findings: list[dict] # [{file, line, level, rule, message}]
    review_context: str       # 拼装好的 prompt 上下文
    ai_findings: list[dict]   # LLM 返回的结构化评审
    final_comments: list[dict]# 合并排序后的最终结果

    # 控制
    skip_ai: bool             # 无变更或规则全过时跳过 AI
    auto_fix_enabled: bool
    fix_branch: str | None

# --- 节点定义 ---

def parse_diff(state: ReviewState) -> ReviewState:
    """① 解析 git diff, 提取变更文件+行号"""
    ...

def run_rules(state: ReviewState) -> ReviewState:
    """② 规则引擎: SQL注入/NPE/线程安全/资源泄漏"""
    ...

def should_run_ai(state: ReviewState) -> Literal["ai_review", "aggregate"]:
    """条件边: 是否需要 AI 审查"""
    if len(state["changed_files"]) == 0:
        return "aggregate"
    return "ai_review"

def ai_review(state: ReviewState) -> ReviewState:
    """③ LLM Agent 逐文件审查"""
    ...

def aggregate(state: ReviewState) -> ReviewState:
    """④ 合并规则结果 + AI 结果, 去重, 按严重程度排序"""
    ...

def auto_fix(state: ReviewState) -> ReviewState:
    """⑤ 高置信度问题自动生成修复 patch"""
    ...

# --- 构建图 ---

def build_review_graph() -> StateGraph:
    builder = StateGraph(ReviewState)

    builder.add_node("parse_diff", parse_diff)
    builder.add_node("run_rules", run_rules)
    builder.add_node("ai_review", ai_review)
    builder.add_node("aggregate", aggregate)
    builder.add_node("auto_fix", auto_fix)

    builder.set_entry_point("parse_diff")
    builder.add_edge("parse_diff", "run_rules")
    builder.add_conditional_edges("run_rules", should_run_ai, {
        "ai_review": "ai_review",
        "aggregate": "aggregate"
    })
    builder.add_edge("ai_review", "aggregate")
    builder.add_conditional_edges("aggregate", lambda s: "auto_fix" if s["auto_fix_enabled"] else END, {
        "auto_fix": "auto_fix",
        END: END
    })
    builder.add_edge("auto_fix", END)

    return builder.compile()
```

## 3. 核心模块详解

### 3.1 Diff 解析（确定性工程）

```python
import git
from unidiff import PatchSet

def parse_diff(state: ReviewState) -> ReviewState:
    repo = git.Repo(state["repo_path"])
    diff_text = repo.git.diff(f"{state['base_branch']}...{state['target_branch']}")

    changed = []
    patch = PatchSet(diff_text)
    for patched_file in patch:
        changed.append({
            "path": patched_file.path,
            "diff": str(patched_file),
            "added_lines": [
                {"lineno": h.target_start + i, "content": line.value}
                for h in patched_file
                for i, line in enumerate(h) if line.is_added
            ],
            "context_lines": _extract_context(patched_file)
        })
    state["changed_files"] = changed
    return state

def _extract_context(patched_file) -> str:
    """提取变更文件的完整内容，给 LLM 做上下文"""
    with open(patched_file.path) as f:
        return f.read()
```

### 3.2 规则引擎（确定性，零 Token 消耗）

```yaml
# rules/sql-injection.yaml
rules:
  - id: SQL_INJECTION_001
    severity: CRITICAL
    pattern: 'Statement\.execute\w*\(\s*\w+\s*\+\s*'
    message: "SQL 注入风险: 字符串拼接构造 SQL"
    suggestion: "改用 MyBatis 参数绑定 #{} 或 PreparedStatement"

  - id: SQL_INJECTION_002
    severity: CRITICAL
    pattern: '("SELECT|"INSERT|"UPDATE|"DELETE).*\+\s*\w+'
    message: "SQL 注入风险: 拼接用户输入到 SQL 语句"

  - id: NPE_001
    severity: WARNING
    pattern: '(\w+)\.(\w+)\(.*\)(?!.*\1\s*!=\s*null)'
    message: "潜在空指针: 调用前未判空"
    suggestion: "调用前加 if (xxx != null) 或使用 Optional"

  - id: RESOURCE_LEAK_001
    severity: WARNING
    pattern: '(InputStream|OutputStream|Connection|Stream)\s+\w+\s*='
    message: "资源可能未关闭"
    suggestion: "使用 try-with-resources 自动关闭"
```

```python
import re, yaml

def run_rules(state: ReviewState) -> ReviewState:
    with open(state["rules_config"]) as f:
        rules = yaml.safe_load(f)["rules"]

    findings = []
    for f in state["changed_files"]:
        for line in f["added_lines"]:
            for rule in rules:
                if re.search(rule["pattern"], line["content"]):
                    findings.append({
                        "file": f["path"],
                        "line": line["lineno"],
                        "level": rule["severity"],
                        "source": "RULE_ENGINE",
                        "rule_id": rule["id"],
                        "message": rule["message"],
                        "suggestion": rule.get("suggestion", "")
                    })
    state["rule_findings"] = findings
    return state
```

### 3.3 AI Agent 审查（LangChain + LLM）

```python
from langchain_openai import ChatOpenAI
from langchain_core.messages import SystemMessage, HumanMessage

REVIEW_SYSTEM_PROMPT = """你是一个资深 Java 代码评审员。审查以下代码变更，只报告需要改进的问题。

审查维度：
1. 线程安全：共享变量是否同步、是否有竞态条件
2. 事务边界：@Transactional 范围是否合理
3. 异常处理：是否存在空 catch、吞异常
4. 性能问题：是否有 N+1 查询、不必要的循环内 IO
5. 安全漏洞：是否有越权风险、敏感信息泄露
6. 代码坏味道：过长方法、重复代码、过度嵌套

返回格式（JSON）：
{
  "findings": [
    {
      "file": "路径",
      "line": 行号,
      "level": "CRITICAL|WARNING|INFO",
      "category": "线程安全|事务|异常|性能|安全|坏味道",
      "message": "问题描述",
      "suggestion": "修复建议"
    }
  ]
}

注意事项：
- 只报告真实存在的问题，不要编造
- 行号必须精确，对应到 diff 中的新增行
- 如果没有问题，返回 {"findings": []}
"""

def ai_review(state: ReviewState) -> ReviewState:
    llm = ChatOpenAI(
        model=state["model"],
        base_url="https://api.deepseek.com",
        api_key=os.environ["DEEPSEEK_API_KEY"],
        temperature=0.1  # 低温度保证稳定
    )

    all_findings = []
    for f in state["changed_files"]:
        # 构造精简的 prompt（省 token）
        user_msg = f"""文件: {f['path']}
变更 diff:
```
{f['diff']}
```
上下文 (完整文件):
```
{f['context_lines']}
```
"""
        response = llm.invoke([
            SystemMessage(content=REVIEW_SYSTEM_PROMPT),
            HumanMessage(content=user_msg)
        ])

        result = json.loads(response.content)
        all_findings.extend(result.get("findings", []))

    state["ai_findings"] = all_findings
    return state
```

### 3.4 结果聚合 + 去重

```python
from difflib import SequenceMatcher

def aggregate(state: ReviewState) -> ReviewState:
    """合并规则引擎 + AI 结果，去重，按严重程度排序"""
    all_findings = state["rule_findings"] + state["ai_findings"]

    # 去重: 同一文件+同一行、语义相似的合并
    deduped = []
    for f in all_findings:
        is_dup = False
        for existing in deduped:
            if (f["file"] == existing["file"]
                and abs(f["line"] - existing["line"]) <= 2
                and SequenceMatcher(None, f["message"], existing["message"]).ratio() > 0.7):
                # 合并: 保留更严重的 level
                existing["source"] = "MERGED"
                is_dup = True
                break
        if not is_dup:
            deduped.append(f)

    # 排序: CRITICAL > WARNING > INFO
    severity_order = {"CRITICAL": 0, "WARNING": 1, "INFO": 2}
    deduped.sort(key=lambda x: (severity_order.get(x["level"], 9), x["file"], x["line"]))

    state["final_comments"] = deduped
    return state
```

### 3.5 自动修复（高置信度）

```python
FIX_SYSTEM_PROMPT = """根据评审意见，生成代码修复 patch。
只生成修改后的代码片段，不要修改评审意见未指出的部分。
返回格式: {"file": "...", "line": 行号, "original": "...", "fixed": "..."}
"""

def auto_fix(state: ReviewState) -> ReviewState:
    """仅对规则引擎产出的 CRITICAL 问题自动修复"""
    fixable = [
        f for f in state["final_comments"]
        if f["source"] == "RULE_ENGINE" and f["level"] == "CRITICAL"
    ]
    if not fixable:
        return state

    llm = ChatOpenAI(model=state["model"], temperature=0)
    branch = f"auto-fix/{state['target_branch']}"
    repo = git.Repo(state["repo_path"])
    repo.git.checkout("-b", branch)

    for f in fixable[:5]:  # 最多修 5 个
        response = llm.invoke([
            SystemMessage(content=FIX_SYSTEM_PROMPT),
            HumanMessage(content=f"问题: {f['message']}\n建议: {f['suggestion']}\n文件: {f['file']}")
        ])
        fix = json.loads(response.content)
        _apply_fix(f["file"], fix["line"], fix["original"], fix["fixed"])

    repo.git.add(A=True)
    repo.git.commit("-m", f"auto-fix: {len(fixable)} issues")
    state["fix_branch"] = branch
    return state
```

## 4. CI 集成

### 4.1 Docker 镜像

```dockerfile
# Dockerfile
FROM python:3.12-slim
RUN apt update && apt install -y git && pip install langgraph langchain-openai gitpython unidiff pyyaml
COPY . /app
WORKDIR /app
ENTRYPOINT ["python", "-m", "code_review.cli"]
```

### 4.2 GitHub Actions 集成

```yaml
# .github/workflows/ci.yml 里加一步
- name: AI Code Review
  if: github.event_name == 'pull_request'
  uses: docker://ghcr.io/你的用户名/ai-code-review:latest
  env:
    DEEPSEEK_API_KEY: ${{ secrets.DEEPSEEK_API_KEY }}
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  with:
    args: >
      --repo ${{ github.workspace }}
      --base origin/${{ github.base_ref }}
      --target origin/${{ github.head_ref }}
      --pr ${{ github.event.pull_request.number }}
      --output github-comment
      --auto-fix
```

## 5. 项目结构

```
ai-code-review/
├── Dockerfile
├── pyproject.toml
├── code_review/
│   ├── __init__.py
│   ├── cli.py              # argparse 入口
│   ├── orchestrator.py     # LangGraph 图定义 + 编译
│   ├── nodes/
│   │   ├── parse_diff.py   # ① Git diff 解析
│   │   ├── rules_engine.py # ② 正则规则引擎
│   │   ├── ai_reviewer.py  # ③ LLM 审查
│   │   ├── aggregator.py   # ④ 结果合并去重
│   │   └── auto_fixer.py   # ⑤ 自动修复
│   ├── rules/
│   │   ├── sql-injection.yaml
│   │   ├── npe.yaml
│   │   ├── thread-safety.yaml
│   │   └── resource-leak.yaml
│   ├── prompts/
│   │   └── reviewer.py     # System Prompt 模板
│   └── output/
│       ├── github_comment.py # 输出到 PR Comment
│       ├── json_report.py    # JSON 报告文件
│       └── ci_annotation.py  # GitHub Actions annotation
└── tests/
    └── ...
```

## 6. 实施路线

| 阶段 | 内容 | 预估 |
|---|---|---|
| **P0** | Diff 解析 + 规则引擎 + CLI 入口 | 3 天 |
| **P1** | LangGraph 搭建 + AI Agent 审查节点 | 3 天 |
| **P2** | 结果聚合 + GitHub PR Comment 输出 | 2 天 |
| **P3** | Docker 打包 + CI 集成 | 1 天 |
| **P4** | 自动修复（高置信度问题） | 3 天 |
| **P5** | 更多规则 + AST 分析 + 缓存优化 | 持续迭代 |

## 7. 关键设计决策

| 决策 | 选择 | 原因 |
|---|---|---|
| 框架 | LangGraph | 状态机天然适合评审流程，条件分支 + 并行文件审查 |
| 语言 | Python 3.12 | AI 库生态最全，改 prompt 立即生效 |
| AI 模型 | DeepSeek-Chat | 你已有 key，中文理解好 |
| 分发方式 | Docker 镜像 | CI 环境即拉即用，不需要装依赖 |
| 规则存储 | YAML 文件 | 增删规则不用改代码 |
| 修复策略 | 只修规则引擎产物 | AI 产物可能有假阳性，规则引擎假阳性率 < 5%