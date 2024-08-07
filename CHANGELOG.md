# CHANGELOG
All notable changes to this project are documented in this file.

Inspired from [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)

## [Unreleased 3.0](https://github.com/opensearch-project/flow-framework/compare/2.x...HEAD)
### Features
### Enhancements
### Bug Fixes
### Infrastructure
- Set Java target compatibility to JDK 21 ([#730](https://github.com/opensearch-project/flow-framework/pull/730))

### Documentation
### Maintenance
### Refactoring

## [Unreleased 2.x](https://github.com/opensearch-project/flow-framework/compare/2.14...2.x)
### Features
- Support editing of certain workflow fields on a provisioned workflow ([#757](https://github.com/opensearch-project/flow-framework/pull/757))
- Add allow_delete parameter to Deprovision API ([#763](https://github.com/opensearch-project/flow-framework/pull/763))

### Enhancements
- Register system index descriptors through SystemIndexPlugin.getSystemIndexDescriptors ([#750](https://github.com/opensearch-project/flow-framework/pull/750))

### Bug Fixes
- Handle Not Found exceptions as successful deletions for agents and models ([#805](https://github.com/opensearch-project/flow-framework/pull/805))
- Wrap CreateIndexRequest mappings in _doc key as required ([#809](https://github.com/opensearch-project/flow-framework/pull/809))
- Have FlowFrameworkException status recognized by ExceptionsHelper ([#811](https://github.com/opensearch-project/flow-framework/pull/811))

### Infrastructure
### Documentation
### Maintenance
### Refactoring
- Improve Template and WorkflowState builders ([#778](https://github.com/opensearch-project/flow-framework/pull/778))
