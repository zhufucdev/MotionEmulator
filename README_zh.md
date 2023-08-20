# MotionEmulator

<img src="art/MotionEmulator.svg" width="200">

[English Version](README.md) | 中文文档

Motion Emulator是个模拟连续定位和传感器变化的应用平台。
它支持多种方式，如Xposed和开发者选项。

## 使用场景

如果你是不幸的中国大学生，或许体验过 _校园跑_ 

尽管教职工总希望我们在夕阳下跑阳光长跑，我想做些有创造性的事情来让生活更轻松一点

## 使用方法

要了解最新、最全面的使用方法和注意事项，请参阅[Steve的博客](https://zhufucdev.com/article/G1lNhmtzI5-RQnVmYEbXm)

## 构建指南

如果你是开发者，请使用最新的Android Studio金丝雀版（当前是Hedgehog | 2023.1.1 Canary 15）构建和维护这个项目，
因为这个项目十分的激进

项目使用了高德地图和Google Maps的API，你得申请些自己的，网址在[这儿](https://console.amap.com/dev/key/app)和
[这儿](https://developers.google.com/maps/documentation/android-sdk/start)

申请完别忘了做些事情
```shell
echo amap.web.key="<Your Key>" >> local.properties
echo AMAP_SDK_KEY="<Your Key>" >> local.properties
echo GCP_MAPS_KEY="<Your Key>" >> local.properties
```

我的私有服务被用来提供一些在线特性，例如自检查更新。这些服务是可选的，并且不会被包括在
第三方构建中。

但还是可以用你自己的服务来构建的
```shell
cd app
echo SERVER_URI="<Your Server>" >> server.properties
echo PRODUCT="<You Decide>" >> server.properties

cd ../mock_location_plugin
echo SERVER_URI="<Your Server>" >> server.properties
echo PRODUCT="<You Decide>" >> server.properties
```

`SERVER_URI`是一个HTTP/HTTPS的RESTful API，它实现了特定的一些协议。你可以通过
[查看我的代码库](https://github.com/zhufucdev/api.zhufucdev)来了解这是一个怎样的协议。

顺便说一句，万一你不熟悉Android开发，你要把自己的SDK填进去，就像这样：
```shell
echo sdk.dir=<Your SDK Full Path> >> local.properties
```

## 营业执照

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

## 特别鸣谢

- [wandergis/coordtransform](https://github.com/wandergis/coordtransform) 的地图坐标系转化算法
