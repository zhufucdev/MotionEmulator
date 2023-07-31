# MotionEmulator
<img src="art/MotionEmulator.svg" width="200">

English Version | [中文文档](README_zh.md)

Motion Emulator is an Xposed module that allows 
you to mock location and sensor data, but in a continuous manner.


## Scenarios

Trick your fitness app or your favourite game. Make you king of the world

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

### For users whose targeting location is in China mainland

Due to mapping restrictions, ensure that **coordinate system**
of the pending trace in Manage -> Trace page is set to **GCJ02**

If you are curious, go ahead to
[Restrictions on geographic data in China](https://en.wikipedia.org/wiki/Restrictions_on_geographic_data_in_China)

## Snapshot Builds

If you are bored, it's possible to try something new, as I host
a private CI for snapshot builds
[here](https://build.zhufucdev.com/job/Motion%20Emulator/)

It's worth noticing that some of these are debug builds, whose
signature is very different from the release ones. If you wanted
to install, you were about to uninstall first

## Build Instructions

Build and maintain this project with the latest Android Studio Canary
(currently Hedgehog | 2023.1.1 Canary 14) because this project is pretty
radical

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
which is optional and shouldn't be included in unofficial builds

However, it is still possible to build with your own service
```shell
cd app
echo SERVER_URI="<Your Server>" >> server.properties
echo PRODUCT_NAME="<You Decide>" >> server.properties

cd ../mock_location_plugin
echo SERVER_URI="<Your Server>" >> server.properties
echo PRODUCT_NAME="<You Decide>" >> server.properties
```

The `SERVER_URI` is supposed to be an HTTP/HTTPS RESTful that implements
a certain protocol. You can get an example by 
[looking at my codebase](https://github.com/zhufucdev/api.zhufucdev)

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