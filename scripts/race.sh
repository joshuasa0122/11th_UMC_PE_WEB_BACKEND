#!/bin/bash
# 같은 POST 요청을 N개 동시에 보내고 응답을 모아 보여줘요.
# 사용법: ./race.sh <경로> '<JSON>' [N]   예) ./race.sh /members '{"email":...}' 20
URL_PATH=$1; BODY=$2; N=${3:-20}; OUT=$(mktemp -d)
for i in $(seq $N); do
  curl -s -o "$OUT/$i" -w '%{http_code}' -X POST "localhost:8080$URL_PATH" \
    -H 'Content-Type: application/json' -d "$BODY" > "$OUT/$i.status" &
done
wait
echo "== ${N}개 동시 요청 결과 (개수 / 상태 / 응답) =="
for i in $(seq $N); do echo "$(cat $OUT/$i.status) $(cat $OUT/$i)"; done | sort | uniq -c
rm -r "$OUT"
