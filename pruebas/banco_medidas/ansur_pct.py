# percentiles 1, 5, 50, 95 y 99 de cada medida de ANSUR II en fracción de la estatura → ansur_pct.json (para anal.js)
import csv,json,numpy as np,os
A=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','modelo','peso','ANSUR_II_{}.csv')
R=[];[R.extend(csv.DictReader(open(A.format(s),encoding='latin-1'))) for s in ['MALE','FEMALE']]
col=lambda k:np.array([float(x[k]) for x in R]);S=col('stature')
M={'bid':'bideltoidbreadth','cb':'chestbreadth','cd':'chestdepth','wb':'waistbreadth','wd':'waistdepth','hb':'hipbreadth','bd':'buttockdepth','th':'thighcircumference','cf':'calfcircumference','ne':'neckcircumference','lt':'lowerthighcircumference'}
json.dump({k:[round(float(np.percentile(col(v)/S,p)),4) for p in (1,5,50,95,99)] for k,v in M.items()},open('ansur_pct.json','w'))
