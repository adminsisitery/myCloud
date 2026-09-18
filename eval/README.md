# Agent 功能评估

这个目录提供固定任务集和可选的真实模型评估器。默认只列出用例；只有显式添加 `--run` 才向本机测试后端发送请求。评估器不会确认文件操作，会拒绝本次生成的待确认建议，并保留任务记录供查看。

使用独立测试账号准备以下文件，名称保持一致：

- `agent-eval-contract.txt`：包含“试运行验收条件为连续运行七天、提供验收报告”。
- `agent-eval-plan-a.txt`：包含“交付周期两周，验收要求提供测试报告”。
- `agent-eval-plan-b.txt`：包含“交付周期三周，验收要求提供测试报告和培训”。
- 两份内容完全相同但文件名不同的文本，用于 SHA256 重复检测。
- `agent-eval-unreadable.pdf`：仅包含扫描图片的 PDF，用于验证无法提取时是否诚实说明。

需要语义检索的文件应先完成项目原有的向量索引流程。让执行前后的根目录没有 `agent-eval-folder`、`agent-eval-project` 和 `agent-eval-checklist.md`，避免名称冲突影响用例。

```powershell
python eval/run_agent_eval.py
$env:MYCLOUD_EVAL_TOKEN = '<独立测试账号的登录令牌>'
python eval/run_agent_eval.py --run --base http://127.0.0.1:8080 --model <已配置模型名> --output agent-evaluation.json
```

可用 `--case document_summary --case compare_documents` 只执行指定用例。评估结果包括工具调用、任务状态、来源数量、用量与耗时。自动断言通过不等于内容质量达标：逐项检查引用是否真正支持结论、文档对比是否完整，以及无法读取时是否编造内容。

确认后续跑、幂等、权限隔离、撤销、配额和未知结果核实由 `AgentPersistenceTest` 等确定性测试覆盖。模型版本升级时重新执行本任务集，记录模型名、测试语料版本和报告，不编造成功率。
