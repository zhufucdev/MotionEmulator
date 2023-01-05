# MotionEmulator

<img src="art/MotionEmulator.svg" width="200">

[English Version](README.md) | 中文文档

带有传感器支持的Android运动模拟器

## 为啥要用

如果你是不幸的中国大学生，想必对 _校园跑_ 比较熟悉

尽管教职工总希望我们在夕阳下跑阳光长跑，我想做些有创造性的事情来让生活更轻松一点

## 咋用

* 打开运动记录器
* 攥紧手机，搞个100m速跑，跑完别忘了按`停`
* 在地图上画个圈儿，表示你想要在那儿模拟跑，或许绕中国大陆画是个好主意
* 设置好速度和圈数
* Make more time

### 加盐
现在，每次模拟，跑的路径都是完全相同的

要让它不那么可疑

* 打开`管理`页面，找到那个路径
* `新建`一个`随机因子`，比如`x`
* 加点盐，比如`旋转`
* 展开你刚加的盐，然后开始敲键盘，加入一些你喜欢点数学表达式，并设法让其中含有`x`

便条：可能需要一点线性代数的知识

## 构建指南

如果你是开发者，请使用Android Studio或Jetbrains IDEA构建和维护这个项目


项目使用了高德地图API，你得申请一个自己的，网址在[这儿](https://console.amap.com/dev/key/app)

申请完别忘了做些事情
```shell
cd app/src/main/res/values
touch amap.xml
## Android SDK ##
echo "<?xml version=\"1.0\" encoding=\"utf-8\"?>" >> amap.xml
echo "<resources><string name=\"amap_api_key\" translatable=\"false\">$android_sdk_key</dimen></resources>" >> amap.xml
## REST API ##
echo $rest_api >> local.properties
```

## 营业执照

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

## 特别鸣谢

- [wandergis/coordtransform](https://github.com/wandergis/coordtransform) 的地图坐标系转化算法