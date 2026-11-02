FROM python:3.12-alpine

WORKDIR /app
COPY relay.py .

# SHUTDOWN_CMD에서 ssh를 쓸 때만 주석을 푼다 (alpine 기본 이미지에는 ssh 클라이언트가 없다).
# RUN apk add --no-cache openssh-client

CMD ["python", "-u", "relay.py"]
