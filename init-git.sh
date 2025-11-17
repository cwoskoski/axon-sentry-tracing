#!/bin/bash
# Initialize git repository and commit initial documentation

cd /home/chadw/repo/axon-sentry-tracing

# Initialize git if not already done
if [ ! -d .git ]; then
    echo "Initializing git repository..."
    git init
fi

# Add all files
echo "Adding files to git..."
git add .

# Create initial commit
echo "Creating initial commit..."
git commit -m "feat: Initial project documentation and technical issue breakdown

- Add comprehensive project README with quick start guide
- Add detailed implementation issues (001-009) in docs/issues/
- Add CLAUDE.md for development guidance
- Add complete issue index and roadmap
- Document all phases from foundation through production readiness

Documentation includes:
- Project setup and Gradle configuration
- Core domain model (TraceContext, SpanAttributes, Configuration)
- OpenTelemetry to Sentry bridge implementation
- Command, event, and query tracing interceptors
- Spring Boot auto-configuration
- Example Spring Boot application

Each issue contains:
- Comprehensive implementation guidance
- Kotlin code examples
- Testing requirements and acceptance criteria
- Integration points and resources
- Definition of done checklist

This provides a complete blueprint for implementing the axon-sentry-tracing
library with Sentry integration for Axon Framework via OpenTelemetry."

echo ""
echo "✅ Git repository initialized and initial commit created"
echo ""
echo "Repository status:"
git status
echo ""
echo "Recent commits:"
git log --oneline -n 1
