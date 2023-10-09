#build stage
FROM golang:1.20.7-bookworm AS builder
WORKDIR /go/src/app
COPY go.mod .
COPY go.sum .
RUN go mod download
COPY . .
RUN go build -o /go/bin/app

#final stage
FROM golang:1.20.7-bookworm
COPY --from=builder /go/bin/app /app
ENTRYPOINT /app
LABEL Name=tokens Version=0.1.0

ENV PORT=9000
ENV CONF_DIR=/conf
EXPOSE 9000
EXPOSE 9001
