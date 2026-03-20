FROM golang:1.23-alpine AS builder

WORKDIR /app
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -o gin_template main.go

FROM alpine:latest

RUN apk add --no-cache ca-certificates
WORKDIR /root
COPY --from=builder /app/gin_template .
EXPOSE 8080
CMD ["./gin_template"]
