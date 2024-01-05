# MotionEmulator
<img src="art/MotionEmulator.svg" width="200">

English Version | [中文文档](README_zh.md)

Motion Emulator is an application platform that allows 
you to mock location and sensor data using different methods,
including Xposed and debugging options.

## Scenarios

Trick your fitness app or your favourite game. Make you king of the world.

## Usage

To learn about the latest software and its tricks, refer to
[Steve's Blog](https://zhufucdev.com/article/RTyhZArsyD2JKPbdHEviU).

## Build Instructions

Build and maintain this project with the latest Android Studio Canary
(currently Hedgehog | 2023.1.1 Canary 15) because this project is pretty
radical.

This app contains sdk from Amap and Google Maps, thus **api keys** are
required.
Obtain them from [here](https://console.amap.com/dev/key/app)
[and here](https://developers.google.com/maps/documentation/android-sdk/start)
```shell
echo amap.web.key="<Your Key>" >> local.properties
echo AMAP_SDK_KEY="<Your Key>" >> local.properties
echo GCP_MAPS_KEY="<Your Key>" >> local.properties
```

My own service is involved to provide online features like self update,
which is optional and shouldn't be included in unofficial builds.

However, it is still possible to build with your own service.
```shell
cat >> local.properties << EOF
server_uri="<Your Server>"
product="<You Decide>"
EOF
```

The `server_uri` is supposed to be an HTTP/HTTPS RESTful that implements
a certain protocol. You can get an example by 
[looking at my codebase](https://github.com/zhufucdev/api.zhufucdev).

By the way, in case you are not familiar with Android dev, fill in
your own SDK like so:
```shell
echo sdk.dir=<Your SDK Full Path> >> local.properties
```

## License

```
Copyright 2022-2023 zhufucdev

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## Special Thanks

- [wandergis/coordtransform](https://github.com/wandergis/coordtransform) for its map coordinate fixing algorithm
