# Using Windows

We recommend using the Windows Subsystem for Linux (WSL2) or use Multipass to create VMs.  If you've never used either then Multipass is probably the quickest way to start initially.

## Multipass

- Setup [Multipass](https://multipass.run/) on Windows (recommened to enable Hyper_V) 
- From a Windows Command Prompt

```
multipass launch --name fabric-dev --disk 80G --cpus 8 --mem 8G --cloud-init https://raw.githubusercontent.com/hyperledgendary/full-stack-asset-transfer-guide/main/infrastructure/multipass-cloud-config.yaml
```

## Using VSCode
- Setup [vscode](https://code.visualstudio.com/) and make sure you've the [remote development extension pack ](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.vscode-remote-extensionpack)installed

- Find out the IP address of the machines thats created - `multipass list` will show you this. For example

```
C:\Users\014961866>multipass list
Name                    State             IPv4             Image
primary                 Running           172.31.125.88    Ubuntu 20.04 LTS
fabric-dev              Running           172.31.118.103   Ubuntu 20.04 LTS
                                          172.17.0.1
```

- You will need to find the private ssh key that multipass uses; this _should_ be at `C:\ProgramData\Multipass\data\ssh-keys\id_rsa`
- Copy this to you home directory (otherwise SSH will not use the file as it's 'too open')

```
copy C:\ProgramData\Multipass\data\ssh-keys\id_rsa %HOMEDRIVE%%HOMEPATH%\.ssh\multipass_id_rsa
```

- In VSCode, click on the remote development icon in the toolbar, and in the *Remote Explorer*, choose *SSH Targets*
- In the title bar of *SSH Targets*, click on the cog, and pick the default configuration file.
- Create an entry in this configuration file 
  - change the HostName to the IP of the multipass created VM
  - ensure the identity file points to the file you copied
  - you can change the `fabric-dev` name if you have multiple entries

```
Host fabric-dev
  HostName 172.31.118.103
  User ubuntu
  Port 22
  StrictHostKeyChecking no
  PasswordAuthentication no
  IdentityFile C:/Users/<your user>/.ssh/multipass_id_rsa
  IdentitiesOnly yes
  LogLevel FATAL
```

- When save, an entry for *fabric-dev* will appear in the *SSH Targets* view
- Click on the 'Open Window' icon next to it
- First time you'll be asked to confirm the system is Linux, and VSCode will setup it's remote server.
- Then you're good to go with browsing files, and also using the inbuilt terminal.
