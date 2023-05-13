# Updater

My implementation of self-check updater

The backend is implemented as an Edge function in 
[this repo](https://github.com/zhufucdev/api.zhufucdev)

## Getting started

To get started, set up your server or serverless function or
edge computing or potato battery, then do

```kotlin
val updater = Updater("<API URI>", "<Product alias>", context)
val update = updater.check()
if (update != null) {
    updater.download()
}

@Composable
fun App() {
    
    updater.progress
}
```