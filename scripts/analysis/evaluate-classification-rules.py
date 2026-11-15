#!/usr/bin/env python3
"""분류 명세 검토용 오프라인 평가 도구. 제품 분류기와 DB 갱신 기능은 포함하지 않는다.

입력 텍스트로만 분류한 뒤 ID를 이용해 고정 정답과 비교한다.
Python 표준 라이브러리만 사용한다. 모든 경로는 CLI로 주입할 수 있다.
"""
import argparse,json,re,hashlib,unicodedata,collections,pathlib
from functools import lru_cache
from datetime import date
CFG={}
@lru_cache(maxsize=4096)
def compiled(pattern):
 for term in CFG.get('shortTokens',[]):
  pattern=re.sub(r'(?<![A-Za-z0-9_])'+term+r'(?![A-Za-z0-9_])',lambda m:r'(?<![A-Za-z0-9])'+m.group()+r'(?![A-Za-z0-9])',pattern)
 return re.compile(pattern,re.I)
def rx(pattern,text):return bool(compiled(pattern).search(text))
def canonical(s):return unicodedata.normalize('NFKC',s).replace('\r\n','\n').replace('\r','\n')
def first(pattern,s,start=0):return re.compile(pattern).search(s,start)
def before(s,patterns):
 ends=[m.start() for p in patterns if (m:=first(p,s))]
 return s[:min(ends)] if ends else s

def extract(title,body):
 p=CFG['parser'];b=canonical(body);start=first(p['dutiesStart'],b);pos=first(p['positionStart'],b)
 req=first(p['requiredStart'],b);preferred=first(p['preferredStart'],b)
 required=before(b[req.end():],[p['preferredStart'],p['contentStop']]) if req else ''
 position=before(b[pos.end():],[p['dutiesStart'],p['requiredStart'],p['preferredStart'],p['contentStop']]) if pos else (b[:start.start()] if start else '')
 if start:
  duty=before(b[start.end():],[p['requiredStart'],p['preferredStart'],p['contentStop']]);mode='structured'
 elif pos:
  duty=before(b[pos.end():],[p['requiredStart'],p['preferredStart'],p['contentStop']]);mode='position-fallback'
 else:
  duty=before(b,[p['requiredStart'],p['preferredStart'],p['contentStop']]);mode='unstructured-fallback'
 # Each line/bullet remains separate. Two adjacent sentences may only share references within that bullet.
 units=[]
 for line in duty.splitlines():
  line=re.sub(r'^\s*[•·▪▶\-]+\s*','',line).strip()
  if len(line)<8:continue
  if rx(r'채용 절차|커피챗|지원하기|전형 일정|면접|이력서|포트폴리오|우대사항|優遇|Nice to have',line):continue
  # Strip negative/future-only sentences but keep concrete assigned branch work.
  if rx(r'하지 않|하지않|않습니다|not responsible|필요에 따라|관심이 있|경험이 있으면|해본 경험|해 본 경험|개발 경험|운영 경험|경험이 있는 분|있으신 분|보신 분|가지신 분|필요시|필요하다면|필요하면|将来的|可能性があります|years? of|years? experience|Bachelor|Master|you have|experienced in|experience with|experience in|experience building',line):continue
  units.append(line)
 # Position-specific overview can supply duties even when the portal places it before the duties heading.
 if start:
  intro=before(b[:start.start()],[p['requiredStart'],p['preferredStart'],p['contentStop']])
  for line in intro.splitlines():
   if rx(r'successful candidate|this role|本ポジション|you will|社内向けのカスタムAIエージェント',line):units.append(line.strip())
 return {'title':canonical(title),'skills':before(b[req.end():],[p['contentStop']]) if req else '', 'duties':duty,'position':position,'required':required,'units':units,'parserMode':mode}

def check(expr,ctx,accepted):
 if 'match' in expr:
  z=expr['match'];return rx(z['pattern'],ctx[z['field']])
 if 'all' in expr:return all(check(e,ctx,accepted) for e in expr['all'])
 if 'any' in expr:return any(check(e,ctx,accepted) for e in expr['any'])
 if 'not' in expr:return not check(expr['not'],ctx,accepted)
 if 'taggedAny' in expr:return bool(set(expr['taggedAny'])&accepted)
 if 'unit' in expr:
  z=expr['unit'];return any(all(rx(p,u) for p in z['all']) and not any(rx(p,u) for p in z['none']) for u in ctx['units'])
 raise ValueError(expr)

def classify(title,body):
 ctx=extract(title,body);accepted=set();proof={}
 for key in CFG['ruleEvaluationOrder']:
  if check(CFG['rules'][key]['when'],ctx,accepted):
   accepted.add(key)
   proof[key]=[]
   # Capture only units that satisfy a unit predicate used in the accepted expression.
   def walk(e):
    if not check(e,ctx,accepted):return
    if 'unit' in e:
     z=e['unit'];proof[key].extend(u for u in ctx['units'] if all(rx(p,u) for p in z['all']) and not any(rx(p,u) for p in z['none']))
    for op in ('all','any'):
     for q in e.get(op,[]):walk(q)
   walk(CFG['rules'][key]['when'])
   proof[key]=list(dict.fromkeys(proof[key]))[:3]
 if 'fullstack' in accepted:accepted.update(CFG['fullstackAdds'])
 return sorted(accepted),proof,ctx['parserMode']

if __name__=='__main__':
 parser=argparse.ArgumentParser(description='저장 본문에 일반 직군 규칙을 적용하고 고정 정답과 비교한다. DB/네트워크 쓰기 없음.')
 root=pathlib.Path(__file__).resolve().parents[2]
 parser.add_argument('--corpus',required=True,type=pathlib.Path,help='최신 본문 JSONL: id, title, body, company, source')
 parser.add_argument('--rules',type=pathlib.Path,default=root/'docs/classification-rules.v0.5.json')
 parser.add_argument('--gold',type=pathlib.Path,default=root/'docs/analysis/classification-440-2026-11-15.json')
 parser.add_argument('--manifest',type=pathlib.Path,default=root/'docs/analysis/classification-gold-v1.json')
 parser.add_argument('--output',required=True,type=pathlib.Path)
 args=parser.parse_args();CFG=json.loads(args.rules.read_text());manifest=json.loads(args.manifest.read_text())
 goldpath=args.gold
 if hashlib.sha256(goldpath.read_bytes()).hexdigest()!=manifest['referenceSha256']:
  raise ValueError('고정 정답 파일 SHA-256 불일치. 정답을 수정했다면 승인 후 새 버전/manifest를 만들어야 한다.')
 gold=json.loads(goldpath.read_text());raw=[json.loads(line) for line in args.corpus.read_text().split('\n') if line.strip()]
 truth={x['id']:x for x in gold['postings']}
 assert len(truth)==len(raw)==manifest['postingCount']
 assert len({p['id'] for p in raw})==len(raw) and set(truth)=={p['id'] for p in raw}
 results=[];agg={key:collections.Counter() for key in CFG['rules']}
 for p in raw:
  expected=truth[p['id']];assert expected['bodySha256']==hashlib.sha256(p['body'].encode()).hexdigest()
  assert all(expected[k]==p[k] for k in ['title','company','source']), '정답 스냅샷 메타데이터 불일치'
  # Only title/body are passed into the classifier. The evaluator joins IDs AFTER prediction.
  pred,proof,mode=classify(p['title'],p['body']);want=set(expected['proposedCategories']);got=set(pred)
  for key in agg:
   agg[key]['tp']+=int(key in want and key in got);agg[key]['fp']+=int(key not in want and key in got);agg[key]['fn']+=int(key in want and key not in got)
  results.append({'id':p['id'],'company':p['company'],'source':p['source'],'title':p['title'],'gold':sorted(want),'predicted':pred,'missing':sorted(want-got),'extra':sorted(got-want),'exact':want==got,'parserMode':mode,'evidence':{k:[u[:240]+('…' if len(u)>240 else '') for u in v] for k,v in proof.items()}})
 def ratio(n,d):return n/d if d else None
 for z in agg.values():
  z['precision']=ratio(z['tp'],z['tp']+z['fp']);z['recall']=ratio(z['tp'],z['tp']+z['fn']);z['f1']=ratio(2*z['tp'],2*z['tp']+z['fp']+z['fn'])
 totals=collections.Counter({key:sum(z[key] for z in agg.values()) for key in ['tp','fp','fn']})
 summary={'exactPostings':sum(x['exact'] for x in results),'total':len(results),'exactMatchRate':sum(x['exact'] for x in results)/len(results),**dict(totals),'microPrecision':ratio(totals['tp'],totals['tp']+totals['fp']),'microRecall':ratio(totals['tp'],totals['tp']+totals['fn']),'microF1':ratio(2*totals['tp'],2*totals['tp']+totals['fp']+totals['fn']),'macroF1':sum(z['f1'] or 0 for z in agg.values())/len(agg),'predictedUnclassified':sum(not x['predicted'] for x in results),'wronglyUnclassified':sum(not x['predicted'] and bool(x['gold']) for x in results)}
 out={'corpusSha256':hashlib.sha256(args.corpus.read_bytes()).hexdigest(),'evaluatorSha256':hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest(),'evaluationDate':date.today().isoformat(),'snapshotDate':gold['snapshotDate'],'goldVersion':manifest['version'],'rulesVersion':CFG['version'],'rulesSha256':hashlib.sha256(args.rules.read_bytes()).hexdigest(),'goldSha256':hashlib.sha256(goldpath.read_bytes()).hexdigest(),'summary':summary,'byCategory':agg,'results':results}
 args.output.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps(summary,ensure_ascii=False,indent=2))
 print('\nCategory | TP FP FN | precision recall F1')
 for k,z in agg.items():print(k,z['tp'],z['fp'],z['fn'],round(z['precision'] or 0,3),round(z['recall'] or 0,3),round(z['f1'] or 0,3))
