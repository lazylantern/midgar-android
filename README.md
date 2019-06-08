# Midgar UX Tracker - Android SDK

[![CircleCI](https://circleci.com/gh/lazylantern/midgar-android.svg?style=svg)](https://circleci.com/gh/lazylantern/midgar-android) [![GitHub release](https://img.shields.io/github/release-pre/lazylantern/midgar-android.svg)](https://github.com/lazylantern/midgar-android/releases)

## Requirements
 - Android API >= 21
 - Kotlin enabled project
 - AndroidX project. Still using Android support libraries? We can help you migrate!

## Integration
The Midgar Android SDK is written in Kotlin and available through Bintray/JCenter (at the time of writing, Bintray only)

To get started with the Integration, you must first include the SDK in your dependencies.

### Add the SDK dependency

In your `app/build.gradle` file:
```gradle
implementation 'com.lazylantern.midgar:midgar-android:LATEST_VERSION'
```

### Provide the appId res
Create a `string` resource in your app:

```xml
<string name="midgar_app_id">YOUR_APP_ID</string>
```

Replace `YOUR_APP_ID` with the id the Lazy Lantern team provided you with.

### Initialize the SDK
In your `Application`'s `onCreate(...)` method, initialize the SDK:

```java
MidgarTracker.getInstance(this).startTracker(this)
```

That's it! Midgar is ready to work for you. Stay tuned for anomalies reports.
