# DocVault — учебный pet-проект

Полный план, архитектура и этапы: `docs/PLAN.md`. Перед любой задачей читай его и работай в рамках текущего этапа.

## О проекте и обо мне

- Я senior Java backend engineer (15+ лет Java, Spring, Kafka, PostgreSQL, Docker, базовый Kubernetes, базовый AWS). Скоро перехожу в команду authN/authZ, где используется **Keycloak**.
- Это **учебный проект**: цель — разобраться в Keycloak, OIDC/OAuth2, GitHub Actions, Terraform, Amazon ECS, Kubernetes, AWS Lambda/SNS/SQS/DynamoDB. Понимание важнее скорости.
- Общаемся по-русски; код, комментарии, коммиты, имена — на английском.

## Как работать со мной

- Каждый этап начинай с **плана** (plan mode) и жди моего «ок» перед реализацией.
- **Объясняй решения**: почему так, какие альтернативы, что спросят об этом на собеседовании. Коротко, по делу.
- Темы, которые я хочу писать **сам** (ты ревьюишь, подсказываешь, но не пишешь за меня, пока я не попрошу): GitHub Actions YAML, конфигурация Keycloak (realm, clients, mappers) и Spring Security, Terraform для ECS. Boilerplate, Lambda-код, тесты, скрипты — можешь писать сам.
- Маленькие шаги и маленькие PR: одна логическая задача — одна ветка — один PR через CI.
- После завершения задачи отмечай чекбокс в `docs/PLAN.md`. Если решение отличается от плана — обнови план и скажи мне.

## Жёсткие правила

### Бюджет $0

- AWS-аккаунт на **Free plan**. Никогда не создавай: **NAT Gateway, EKS, Fargate (для постоянной работы), RDS, Elastic IP без привязки**, крупные инстансы. Не подключай аккаунт к AWS Organizations.
- ECS только на EC2 (t3.medium, один инстанс, ASG). ALB — только через флаг `enable_alb`, временно, для экспериментов с масштабированием.
- Lambda не кладём в VPC. CloudWatch Logs retention 3–7 дней. ECR lifecycle policy.
- Если что-то стоит денег сверх копеек — предупреди **до** того, как предлагать.

### Безопасность и изменения в AWS

- **`terraform apply`, `terraform destroy`, `sam deploy`, любые AWS CLI-команды, меняющие ресурсы, — только после моего явного подтверждения.** `terraform plan`, `validate`, `fmt` — можно.
- Никаких секретов в репозитории (он публичный): только GitHub Secrets / Variables, SSM Parameter Store, локальный `.env` в `.gitignore`.
- В CI доступ в AWS через **access keys отдельного IAM-пользователя**, хранятся только в GitHub Secrets (лучше в environment `prod`). OIDC на нашем аккаунте недоступен: SCP Free plan запрещает `iam:*Provider*`, а «Activate advanced features» нарушает правила бюджета. Осознанное решение владельца: security учебная, терять нечего. Ключи CI не должны совпадать с ключом `docvault-terraform` (чтобы отзывать независимо). `permissions` в workflows — минимальные.
- Регион AWS: **eu-north-1** (в `eu-central-1` и `us-east-1` аккаунт запрещает базовые операции EC2 через SCP).
- Не делай `git push --force` и не переписывай историю `main`.

## Стек и решения (не менять без обсуждения)

| Область | Выбор |
| --- | --- |
| IdP | Keycloak (последняя стабильная версия), realm `docvault`, импорт из JSON |
| Сервис | `docs-api`: Java 21, Spring Boot 3, Spring Security OAuth2 Resource Server, Maven |
| БД | PostgreSQL (контейнер; в AWS — ECS-сервис с host volume, не RDS) |
| Keycloak SPI | Java, Event Listener → SNS |
| Lambda | Python, boto3 + Powertools for AWS Lambda, pytest |
| События | SNS → SQS (+ DLQ) → Lambda → DynamoDB |
| CI/CD | GitHub Actions, образы в Amazon ECR, тег = git SHA |
| IaC | Terraform (`infra/`), state в S3; serverless — AWS SAM |
| Оркестрация в облаке | Amazon ECS на EC2 |
| Kubernetes | Локально, k3d + Helm, Keycloak Operator |
| HTTPS | Caddy + Let's Encrypt, домен sslip.io / DuckDNS |

## Структура репозитория

```
docs/PLAN.md            # план и архитектура
docs-api/               # Spring Boot сервис
keycloak/               # Dockerfile, realm-export, SPI (Maven-модуль)
lambdas/                # audit-writer, notifier, login-guard (Python) + template.yaml (SAM)
infra/                  # Terraform: bootstrap/, modules/, envs/prod/
k8s/                    # Helm-чарты и манифесты для k3d
.github/workflows/      # CI/CD
docker-compose.yml      # локальное окружение
scripts/                # smoke-test и утилиты
```

## Команды

- Локальное окружение: `docker compose up -d`
- Тесты сервиса: `cd docs-api && ./mvnw verify` (Testcontainers с реальным Keycloak — нужен запущенный Docker)
- Тесты Lambda: `cd lambdas && pytest`
- Terraform: `cd infra/envs/prod && terraform fmt -recursive && terraform validate && terraform plan`

Если команды ещё не работают (этап не дошёл) — сначала создай то, что нужно, по плану текущего этапа.

## Стиль кода

- Java: конструкторная инъекция, records для DTO, без Lombok, понятные имена; тесты на JUnit 5 + AssertJ.
- Security: проверяй `iss`, `aud`, роли мапь из `realm_access` / `resource_access` явно; 401 vs 403 различай.
- Python: type hints, Powertools (Logger, idempotency где нужно), без лишних зависимостей.
- Terraform: модули с `variables.tf` / `outputs.tf`, `for_each` вместо копипасты, теги на всех ресурсах (`project = "docvault"`).
- События: JSON с полями `eventId`, `type`, `occurredAt`, `tenantId`, `actorId`, `payload`; обработчики идемпотентны.
