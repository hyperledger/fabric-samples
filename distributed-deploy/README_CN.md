# 分布式部署

区块链安装部署包的主要功能分为三个部分，分别为新装、扩缩容以及修复。

新装功能主要是根据部署包的配置文件单机或分布式部署`orderer`和`peer`，用户只需配置好相应的域名及ip，即可实现一键部署。

扩缩容功能是针对正在运行的区块链网络，可以对网络中`orderer`和`peer`进行添加节点或减少节点。

修复功能是结合扩缩容功能实现的，对需要修复的`peer`或`orderer`节点进行先缩容后扩容的操作。

## 依赖环境

请确保运行在`Linux`环境上并安装以下的工具：

- jdk8以上版本
- Maven，推荐3.6.0版本以上
- Docker，推荐19.x以上
- Docker-compose，推荐1.23.2以上
- Fabric 1.4.2

## 构建部署包

首先，我们需要先构建一些依赖文件。主要包括，项目的依赖包和`fabric`的二进制文件。我们先构建项目的依赖包，进入项目的根目录，然后执行下面的命令：

```shell
mvn package -DskipTests
```

生成的`jar`包位于*bcp-install-main/target/*目录中。

之后，我们进入`fabric`的代码目录，执行如下命令生成我们需要的二进制文件：

```shell
make release
```

我们将编译好的二进制文件复制到项目的*bcp-install-main/resources/generateInstallPackage/masterPackage/tools/linux*中。

*注意: 主要的二进制文件包括configtxgen, configtxlator和cryptogen*

## 开始部署

默认情况下，我们启动两个`peer`节点，和三个`orderer`节点。

### 复制主/从节点部署包到服务器中

主/从节点部署包主要位于*bcp-install-main/resources/generateInstallPackage*目录中，其中*masterPackage*为主节点部署包，*slavePackage*为从节点部署包。

我们需要将上一小节**构建部署包**中生成的`jar`文件，即`bcp-install.jar`分别复制到*masterPackage*和*slavePackage*中。

![](11)

我们将`peer0`作为主节点服务器，将*masterPackage*目录复制到`peer0`服务器上。同时我们需要将*slavePackage*目录分别复制到`peer1`, `orderer0`, `orderer1`, `orderer2`服务器中。

### 修改initconfig.properties配置

我们首先需要得到`peer0`, `peer1`, `orderer0`, `orderer1`以及`orderer2`所在服务器的**ip**和对应的**端口**。然后修改*masterPackage*目录中的initconfig.properties配置文件。

![](22)

### 执行部署安装脚本

首先执行各个从节点部署包的部署安装脚本，在从节点部署包根目录下执行脚本**start-installService-slave.sh**，脚本有一个必填启动选项“-p”，表示安装目录，必须填写为绝对路径，我们可以使用如下的命令启动从节点部署包:

```shell
./start-installService-slave.sh -p /home/deploy/
```

![](33)

所有从节点部署包启动成功后，在`peer0`服务器上执行主节点部署包目录下的**start-installService-master.sh**脚本，这个脚本有两个必填启动选项，分别是“-m”和“-p”。“-m”表示部署操作，可选值有“newInstall”和“updateNetwork”，“-p”，表示安装目录，必须填写为绝对路径，我们可以使用如下的命令启动主节点部署包:

```shell
./start-installService-master.sh -m newInstall -p /home/deploy
```

![](44)

## 扩缩容部署流程

### 修改配置文件

首先，我们在主节点部署包的根目录下修改**initconfig.propertise**配置文件。

扩容修改的规则为在需要扩容节点的角色域名配置项中**peerHostConfig**或者**ordererHostConfig**添加新节点的**域名**、**ip**、**端口**以及涉及到的其他配置项的信息。

缩容修改的规则为，在需要缩容节点的角色域名的配置项中**peerHostConfig**或者**ordererHostConfig**删除对应的配置信息。

### 运行部署包

我们使用如下的命令启动主节点安装部署包:

```shell
./start-installService-master.sh -o 1 -m updateNetwork -p /home/bcp-master
```

*注意：安装路径要与“newInstall”时一致*

启动新节点的从节点安装部署包, 如果新节点配置在主节点部署包所在服务器上，则无需启动从节点部署包。我们使用如下命令运行:

```shell
./start-installService-slave.sh -o 0 -p /home/bcp-master
```

*注意：安装路径要与“newInstall”时一致*

除了启动这两个节点外，扩缩容不同角色的节点还需启动不同角色所在服务器的从节点部署包，具体的启动规则如下所述:

扩缩容`orderer`：

1. 需启动正在运行所有`orderer`所在服务器的从节点部署包，如果与新节点位于同一服务器无需启动从节点部署包。
2. 需启动正在运行的后台所在服务器的从节点部署包，如果与新节点位于同一服务器无需启动从节点部署包。

扩缩容`peer`：

需启动正在运行的后台所在服务器的从节点部署包，如果与新节点位于同一服务器无需启动从节点部署包。

## 部署包配置文件initconfig.propertise

**initconfig.propertise**文件位于主节点安装部署包的目录中, 文件主要分为五部分的配置，分别为`fabric机构信息配置`, `orderer相关配置`, `peer相关配置`, `区块链管理平台相关配置`, `前端相关配置`。

### orderer配置

ordererDomain: orderer所使用的域名。例：bcplatform.com。

ordererHostConfig：这一项需要填写接入区块链网络的orderer节点host配置信息和开启的访问端口。如果orderer节点有多个，则需要配置多个orderer节点的host配置信息，一般建议至少接入三个orderer节点。左边是域名，右边是IP地址和端口。注意要与ordererDomain一致。例：orderer. bcplatform.com 172.100.10.1:7050

### peer配置

peerDomain：peer所使用的域名。例：bcplatform.com。

peerHostConfig：这一项需要填写接入区块链网络的peer节点host配置信息和开启的访问端口。如果peer节点有多个，则需要配置多个peer节点的host配置信息，一般建议至少接入两个peer节点。左边是域名，右边是IP地址和端口。注意要与peerDomain一致。例：Peer0.orgName.bcplatform.com 172.100.10.4:7051。

metricPortConfig：配置peer的交易数查询端口，要与peerHostConfig配置中的host一一对应。例：Peer0.orgName.bcplatform.com:9443。

### fabric机构信息配置

network: docker在创建环境时用到的网络名称

channelName: 创建的链名称

orgMSPID: 接入链网络的机构MSPID

orgName: 接入链网络的机构名

## 贡献

感谢您考虑提供源代码帮助！我们欢迎互联网上任何人的贡献，并感谢即使是最小的修复！

如果您想为项目做贡献，请分叉，修复，提交并发送请求请求，以供维护人员查看并合并到主要代码库中。

## 许可

项目源代码文件在LICENSE文件中的Apache许可证版本2.0（Apache-2.0）下可用。可在http://creativecommons.org/licenses/by/4.0/获得的知识共享署名4.0国际许可（CC-BY-4.0）下提供**项目文档文件。
