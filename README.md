# MotionEmulator
<img src="art/MotionEmulator.svg" width="200">

English Version | [中文文档](README_zh.md)

Android motion simulator with sensor support.


## Scenarios

If you study in universities in China, there'll be
no necessity to explain it.

Despite how hard the faculty tries to make us take an
after-dinner sunny long run every three days,
we always want to create something innovative to make the life a little
bit easier.

## Usage

* Pick up your phone and turn on the motion recorder
* Take a one-hundred-meter speed run and press `STOP`
* Draw the path you desire to be running on
* Set velocity and repeat count
* Make more time

### Adding salt
Now for each emulation, the path to run on
is exactly the same

To reverse the behavior to make it not so sus

* Turn to the `Manage` page and navigate to the trace
* `Add` some `Random Factor`, saying `x`
* Add some salt, like `Rotation`
* Expand the salt you've just added, and put in some
mathematics expression involving `x` as you like

Note: knowledge of linear algebra may be needed

## Build Instructions

Build and maintain this project with Android Studio
or Intellij IDEA.

This app contains sdk from Amap and Google Maps, thus **api keys** are
required.
Obtain them from [here](https://console.amap.com/dev/key/app)
[and here](https://developers.google.com/maps/documentation/android-sdk/start)
```shell
echo amap.web.key="<Your Key>" >> local.properties
echo AMAP_SDK_KEY="<Your Key>" >> local.properties
echo GCP_MAPS_KEY="<Your Key>" >> local.properties
```

## License

```
Copyright 2022 zhufucdev

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