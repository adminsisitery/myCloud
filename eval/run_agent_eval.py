"""Opt-in local functional evaluation. Never confirms file mutations or prints credentials."""
import argparse, json, os, re, time, urllib.request, urllib.parse
from pathlib import Path

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--run',action='store_true',help='Send requests to the local test backend and consume model tokens')
    parser.add_argument('--base',default='http://127.0.0.1:8080')
    parser.add_argument('--model')
    parser.add_argument('--case',action='append',default=[])
    parser.add_argument('--output',default='agent-evaluation.json')
    args=parser.parse_args()
    cases=json.loads(Path(__file__).with_name('agent-scenarios.json').read_text(encoding='utf-8'))
    if args.case: cases=[c for c in cases if c['id'] in args.case]
    if not args.run:
        print(json.dumps([{'id':c['id'],'prompt':c['prompt']} for c in cases],ensure_ascii=False,indent=2));return
    parsed=urllib.parse.urlparse(args.base)
    if parsed.scheme!='http' or parsed.hostname not in ('localhost','127.0.0.1') or parsed.username or parsed.password:
        parser.error('Only an explicit local HTTP test backend is allowed')
    token=os.environ.get('MYCLOUD_EVAL_TOKEN')
    if not token: parser.error('Set MYCLOUD_EVAL_TOKEN to a dedicated test-user token')
    def api(path,body=None):
        request=urllib.request.Request(args.base.rstrip('/')+path,data=None if body is None else json.dumps(body).encode(),headers={'Authorization':token,'Content-Type':'application/json'})
        return urllib.request.urlopen(request,timeout=330)
    results=[]
    for case in cases:
        started=time.monotonic();run_id=None;tools=[];events=[]
        result={'id':case['id'],'passed':False}
        try:
            import uuid
            with api('/api/ai/agent',{'message':case['prompt'],'conversationId':None,'model':args.model,'requestId':str(uuid.uuid4()),'webSearchEnabled':False}) as response:
                name='message';data=[]
                for raw in response:
                    line=raw.decode('utf-8').rstrip('\r\n')
                    if not line:
                        if data:
                            value='\n'.join(data)
                            if name!='chunk':
                                value=json.loads(value)
                                if name=='agent_started':run_id=value['runId']
                                if name=='agent_tool_start':tools.append(value['name'])
                            events.append(name)
                            if name=='done':break
                        name='message';data=[]
                    elif line.startswith('event:'):name=line[6:].strip()
                    elif line.startswith('data:'):data.append(line[5:].removeprefix(' '))
            if not run_id: raise RuntimeError('No durable task ID returned')
            with api('/api/ai/agent/runs/'+run_id) as response:snapshot=json.load(response)
            run=snapshot['run'];actions=snapshot['actions'];operations={a['operation'] for a in actions}
            assertions={
                'status':run['status'] in case['statuses'],
                'tools':not case['toolsAny'] or bool(set(tools)&set(case['toolsAny'])),
                'no_mutation_executed':all(a['status']=='PENDING' for a in actions),
                'operations':set(case.get('operations',[])).issubset(operations),
                'read_only':not case.get('forbidOperations') or not actions,
                'citation':not case.get('requireCitation') or bool(re.search(r'\]\(file:\d+\)',run['output'])),
                'sources':len(run.get('sourceFileIds',[]))>=case.get('minimumSources',0),
            }
            result.update(passed=all(assertions.values()),assertions=assertions,runId=run_id,tools=tools,status=run['status'],tokens=run['inputTokens']+run['outputTokens'],manualReview=case.get('manualReview'))
        except Exception as exc:
            result['error']=type(exc).__name__ # Response bodies may contain private data; do not write them.
        finally:
            if run_id:
                try:
                    with api('/api/ai/agent/runs/'+run_id) as response:pending=json.load(response)['actions']
                    for action in pending:
                        if action['status']=='PENDING':
                            with api('/api/ai/agent/actions/'+action['id']+'/reject',{}):pass
                except Exception:result['cleanup']='Pending suggestions need manual rejection'
            result['elapsedSeconds']=round(time.monotonic()-started,2);results.append(result)
    output={'kind':'local model functional evaluation','cases':results,'passed':sum(r['passed'] for r in results),'total':len(results),'note':'Pass measures the asserted protocol/tool behaviors; factual quality and retrieval recall still require review.'}
    Path(args.output).write_text(json.dumps(output,ensure_ascii=False,indent=2),encoding='utf-8')
    print(f"Functional assertions: {output['passed']}/{output['total']}. Report: {args.output}")
    raise SystemExit(0 if all(r['passed'] for r in results) else 1)

if __name__=='__main__':main()
