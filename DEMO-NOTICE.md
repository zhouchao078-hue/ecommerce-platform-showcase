# Demo Package Notice

This directory is a sanitized showcase copy of the e-commerce project.

## Included

- Frontend source
- Java backend source
- Python CrewAI ticket copilot source
- Database SQL scripts
- Docker Compose and monitoring configuration
- Performance and concurrency test scripts
- Project documentation

## Excluded

- Local `.env` files and real credentials
- Git history, submodule metadata, and remote configuration
- IDE settings
- Maven targets, frontend builds, and Node dependencies
- Python virtual environments and caches
- Runtime logs and local test output
- Internal handoff and test-report documents

## Before Running

Create a local environment file:

```powershell
Copy-Item .env.example .env
```

Then replace every `change-me` value with credentials for your own local
environment.

The package does not contain:

- Model API keys
- Feishu webhook URLs or signing secrets
- SSH keys
- Production database credentials
- Real server addresses
- User data exports

Run and deployment instructions are in `QUICKSTART.md`. The complete project
documentation is in `README.md`.
