Snapshotter library
===================

Snapshotter is a small library to facilitate writing and executing Snapshot tests.

## What is snapshot testing?
Snapshot testing is a way of writing high-level integration tests that compare values returned by the code with previously saved results.

For example, imagine you're writing a REST API and want to test the endpoints. With snapshot testing the flow will look like:
1. Write endpoint implementation
1. Execute a snapshot test which calls this endpoint
    1. The first time the test is executed, the result will be saved to a snapshot file
    1. All subsequent runs of the same test will compare the result with saved snapshot. If both are the same, the test passes. Otherwise, a test failure is reported.

## Getting started

### Maven
Add jitpack to your repositories:
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

Add Snapshotter dependency:
```xml
<dependency>
    <groupId>com.github.ubitricity</groupId>
    <artifactId>snapshotter</artifactId>
    <version>0.0.1</version>
    <scope>test</scope>
</dependency>
```

### Gradle
Add jitpack to your repositories:
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

Add Snapshotter dependency:
```groovy
dependencies {
    testImplementation 'com.github.ubitricity:snapshotter:0.0.1'
}
```

## Usage

1. Create `Snapshotter` object

kotlin:
```kotlin
val snapshotter = Snapshotter("path_where_you_want_to_keep_snapshots")
```

java: 
```java
Snapshotter snapshotter = new Snapshotter("path_where_you_want_to_keep_snapshots");
```
        
2. Use snapshotter validations

kotlin:
```kotlin
snapshotter.validateSnapshot(data)
```

java: 
```java
snapshotter.validateSnapshot(data);
```

For more examples, please see Snapshotter tests. 

## Features
### Custom serialization module
By default, the snapshots are serialized to human-readable JSON file. To override the serialization module, pass custom implementation as the second argument when initializing `Snapshotter` instance. Serialization module must override the following interface:
```kotlin
interface SnapshotSerializer {
    fun serialize(data: Any?): String
}
```

### Validation config
Additional parameter can be passed to `validateSnapshot` method to alter validation behavior:

```kotlin
ValidationConfig(
    snapshotName: String // Overrides snapshot file name. If left as `null` test case name will be used
    compareMode: CompareMode // Used to alter JSONassert compare mode (see: http://jsonassert.skyscreamer.org/apidocs/index.html)
    ignore: Collection<String> // Can be used to pass a list of field names that should be ignored when comparing snapshots. Useful for excluding IDs, timestamps, etc.
    only: Collection<String> // Opposite of `ignore`, can be used to select fields which should be compared. All other fields will be ignored
)
```

## Motivation
This library is heavily inspired by an awesome [KotlinSnapshot](https://github.com/Karumi/KotlinSnapshot). Unfortunately, their approach to comparing snapshots is based on String equality. For our purposes, where we use snapshots for performing acceptance tests we always compare JSON responses. Using string comparison for JSON objects leads to multiple inconveniences, in particular:
* We need to guarantee that the contents are the same. This is tedious to do for UUIDs and timestamps
* JSON serialization does not guarantee the same order for Arrays and Objects. This may lead to false negative results when comparing the snapshots
* It is impossible to ignore some fields in the response
