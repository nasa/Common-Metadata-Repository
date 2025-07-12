
# Development Guide

## Overview
This guide provides detailed information for developers contributing to the CMR project.

## Project Structure
```
cmr/
├── access-control-app/     # Access control microservice
├── bootstrap-app/          # Bootstrap utilities
├── common-app-lib/         # Common application libraries
├── common-lib/             # Shared libraries
├── dev-system/             # Development environment
├── indexer-app/            # Elasticsearch indexing
├── ingest-app/             # Data ingestion
├── metadata-db-app/        # Metadata database
├── search-app/             # Search functionality
├── system-int-test/        # Integration tests
└── docs/                   # Documentation
```

## Getting Started

### Prerequisites
- Java 17
- Leiningen
- Maven
- Docker
- Git

### Setup Development Environment
1. Clone the repository
2. Run `./bin/cmr setup profile`
3. Update `./dev-system/profiles.clj`
4. Run `./bin/cmr setup dev`

### Running the Application
```bash
# Start REPL for development
./bin/cmr start repl

# In REPL, run:
(reset)

# Or run as JAR
cmr build uberjars
cmr start uberjar dev-system
```

## Contribution Workflow

### 1. Planning Your Contribution
- Check existing issues and PRs
- Create an issue for new features
- Discuss approach with maintainers

### 2. Development Process
```bash
# Create feature branch
git checkout -b feature/your-feature-name

# Make changes
# Add tests
# Update documentation

# Track your contribution
python3 scripts/update-contributors.py
```

### 3. Testing
```bash
# Run unit tests
lein modules utest

# Run integration tests
lein modules itest --skip-meta :oracle

# Run all tests
(run-all-tests)  # In REPL
```

### 4. Documentation
- Update relevant README files
- Add/update code comments
- Update API documentation
- Add examples if applicable

### 5. Submitting Changes
- Create pull request with detailed description
- Include contribution tracking information
- Ensure all tests pass
- Address review feedback

## Code Standards

### Clojure Style Guide
- Follow [Clojure Style Guide](https://github.com/bbatsov/clojure-style-guide)
- Use [Stuart Sierra's ns guidelines](https://stuartsierra.com/2016/clojure-how-to-ns.html)

### Testing Standards
- Write unit tests for pure functions
- Write integration tests for API endpoints
- Maintain test coverage above 80%
- Use descriptive test names

### Documentation Standards
- Document all public functions
- Include usage examples
- Keep README files updated
- Write clear commit messages

## Architecture Guidelines

### Microservices
- Keep services focused and small
- Use proper API boundaries
- Implement health checks
- Include proper logging

### Database Design
- Use appropriate indexes
- Follow naming conventions
- Include migration scripts
- Document schema changes

### API Design
- Follow REST principles
- Use consistent error handling
- Include proper validation
- Version your APIs

## Recognition System

### Contribution Levels
Your contributions are tracked and recognized:

- **⭐ Rising Star**: New contributors (1-4 contributions)
- **🥉 Bronze**: Regular contributors (5-19 contributions)
- **🥈 Silver**: Active contributors (20-49 contributions)
- **🥇 Gold**: Core contributors (50+ contributions)

### Types of Contributions
- 🐛 Bug fixes
- ✨ New features
- 📝 Documentation improvements
- 🎨 UI/UX enhancements
- ⚡ Performance optimizations
- 🔧 Configuration improvements
- 🚀 Deployment enhancements

## Resources

### Documentation
- [API Documentation](api.md)
- [Deployment Guide](deployment.md)
- [Testing Guide](testing.md)

### Community
- GitHub Issues for bug reports
- GitHub Discussions for questions
- Pull Requests for contributions

### Tools
- [CMR CLI Tool](../bin/cmr)
- [Contributor Tracker](../scripts/update-contributors.py)
- [Development Scripts](../bin/)

## Getting Help

1. Check existing documentation
2. Search GitHub issues
3. Create a new issue with detailed description
4. Tag relevant maintainers

Remember: Every contribution, no matter how small, is valuable and will be recognized!
