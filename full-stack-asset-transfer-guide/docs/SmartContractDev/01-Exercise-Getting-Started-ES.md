# Comenzar con un Contrato Inteligente

[ANTERIOR - Introducción](./00-Introduction-ES.md) <==>  [SIGUIENTE - Agregar una Función Transaccional](./02-Exercise-Adding-tx-function-ES.md)

---

Asegúrate de haber replicado/clonado el workshop:

```bash
git clone https://github.com/hyperledger/fabric-samples.git fabric-samples
cd fabric-samples/full-stack-asset-transfer-guide

export WORKSHOP_PATH=$(pwd)
```

Primero por favor revisa que tienes las [herramientas requeridas](../../SETUP.md) para la parte de dev de este workshop (docker, just, weft, node.js, y binario del Fabric peer). Para cerciorarse ejecuta el script `check.sh`

```
${WORKSHOP_PATH}/check.sh
```

Vayamos directo a crear el código para administrar un 'activo'; es mejor tener dos (2) ventanas abiertas, una para correr la 'FabricNetwork' y otra para 'ChaincodeDev'. Puedes querer abrir una tercera ventana para visualizar los logs de la red de Fabric.

## Inicia la Infraestructura de Fabric

Estamos utilizando MicroFab para la infraestructura de Fabric ya que es un único contenedor que es fácil de iniciar.
El contenedor de MicroFab incluye un nodo del servicio de ordenamiento y un proceso peer que esta pre configurado para crear un channel e invocar chaincodes externos.
Tiene ademas credenciales para una organización `org1`, que sera utilizada para ejecutar el peer. Usaremos un usuario administrador de `org1` para interactuar con el entorno.

Usaremos recetas de `just` para ejecutar múltiples comandos. Las recetas `just` son similares a make `make` pero mas fáciles de comprender. Puedes abrir el [justfile](../../justfile) en el directorio raíz del proyecto para ver qué comandos son ejecutados con cada receta.

Inicia el contenedor de MicroFab ejecutando la receta `just`. Esto establecerá algunas propiedades para el ambiente MicroFab e iniciará el contenedor docker de MicroFab. 

```bash
just microfab
```

Esto arrancará el contenedor docker (lo descargara automáticamente si es necesario), y adicionalmente escribirá algunos archivos de configuración/data en el directorio `_cfg/uf`.
```bash
ls -1 _cfg/uf

_cfg
_gateways
_wallets
org1admin.env
org2admin.env
```

Un archivo `org1admin.env` es generado que contiene las variables de entorno necesarias para ejecutar aplicaciones  _con la identidad de org1 admin_. Una segunda organización es creada, con un `org2admin.env` - esto es para ejercicios más adelante y no es requerido para el actual.

Veamos cuáles son las variables de entorno y usemos source para establecer las mismas:

```bash
source _cfg/uf/org1admin.env
cat _cfg/uf/org1admin.env
```

Veremos establecidas éstas variables de entorno:

```bash
# valores de ejemplo
export CORE_PEER_LOCALMSPID=org1MSP
export CORE_PEER_MSPCONFIGPATH=/workshop/full-stack-asset-transfer-guide/_cfg/uf/_msp/org1/org1admin/msp
export CORE_PEER_ADDRESS=org1peer-api.127-0-0-1.nip.io:8080
export FABRIC_CFG_PATH=/workshop/full-stack-asset-transfer-guide/config
export CORE_PEER_CLIENT_CONNTIMEOUT=15s
export CORE_PEER_DELIVERYCLIENT_CONNTIMEOUT=15s
```

Ahora revisemos los tres directorios creados `_msp`, `_gateways`, `_wallets`. Si no tienes mucho tiempo puedes [saltear a la siguiente sección para empaquetar e implementar un chaincode](#empaquetar-e-implementar-el-chaincode-a-fabric).

Primero, el directorio `_msp` contiene los credenciales necesarios del membership services provider (MSP) para ejecutar los comandos CLI del Fabric Peer como org1 admin, incluyendo el certificado público del usuario y la llave privada para firmar las transacciones. La ubicación del MSP esta referenciado en la variable de entorno `CORE_PEER_MSGCONFIGPATH` y contiene los subdirectorios de credenciales que son esperados por el comando CLI del Peer.

En segundo lugar el directorio `_gateways` contiene dos archivos JSON, uno por organización. Este archivo contiene los detalles de la ruta url del Peer al que se pueden conectar los clientes. Los SDKs de clientes Fabric anteriores necesitaban que toda la información estuviese en este archivo, pero los nuevos "Gateway SDKs" quitaron la necesidad para tanto detalle. Los nuevos SDKs para Gateway solo necesitan la ruta del peer y su configuración de TLS. Puedes ver este [código ejemplo](../../applications/ping-chaincode/src/fabric-connection-profile.ts) para poder analizar este archivo fácilmente para el SDK de Gateway.

Por último esta el directorio `_wallets` - que tiene tres subdirectorios, uno para cada organización a saber la de Ordering, Organization 1, y Organization 2. Estos directorios contienen archivos `*.id` que tienen los detalles de identidades y sus respectivos credenciales, parecido al contenido del MSP, pero en un formato JSON que las aplicaciones pueden analizar fácilmente:

```
_wallets
├── Orderer
│   └── ordereradmin.id
├── org1
│   ├── org1admin.id
│   └── org1caadmin.id
└── org2
    ├── org2admin.id
    └── org2caadmin.id
```

`org1admin.id` contiene los credenciales para enviar transacciones desde un administrador de org1.
`org1caadmin.id` contiene los credenciales para crear identidades adicionales en la Autoridad Certificante (CA) de org1.

Tenga en cuenta que cuando Microfab comenzó lanzó automáticamente una Autoridad Certificante que creó estas identidades y sus respectivas credenciales.

Elija uno de los archivos de id y observa el contenido JSON del mismo incluyendo el certificado público y la llave privada:

```bash
cat _cfg/uf/_wallets/org1/org1admin.id | jq
```

Verás el contenido de org1admin.id:

```bash
{
  "credentials": {
    "certificate": "-----BEGIN CERTIFICATE-----\n  xxxx \n-----END CERTIFICATE-----\n",
    "privateKey": "-----BEGIN PRIVATE KEY-----\n  xxxx  \n-----END PRIVATE KEY-----\n"
  },
  "mspId": "org1MSP",
  "type": "X.509",
  "version": 1
}
```

Esta información puede ser utilizada por las aplicaciones cliente. Ver [este código ejemplo](../../applications/ping-chaincode/src/jsonid-adapter.ts) para entender como puedes analizar este archivo para usarlo con el gateway.

En este punto quizás quieras ejecutar `docker logs -f microfab` en una ventana separada para visualizar la actividad - no necesitas configurar nada específico aquí..

## Empaquetar e implementar el chaincode a Fabric

Utilizaremos el patrón Chaincode-As-A-Service (CCAAS) para este chaincode.
Con este patrón, el peer de Fabric peer no inicia un chaincode implementado.
En su lugar, ejecutaremos el chaincode como un proceso externo para que podamos fácilmente y localmente ejecutar los comandos para iniciarlo, detenerlo, actualizarlo y depurarlo.
Sin embargo, aún debemos indicarle al peer dónde se esta ejecutando el chaincode. Esto lo logramos implementando un paquete de chaincode que incluye solamente, en vez del verdadero código fuente del mismo, el nombre del chaincode y la dirección del mismo.

### Empaquetar e implementar el chaincode utilizando la receta `just`.

```bash
just debugcc
```

Veras el id del chaincode y los pasos de implementación como resultado.

### Detalles de este proceso de empaquetar e implementar

Si te gustaría entender en mas detalle el proceso de empaquetar e implementar chaincodes puedes hacer manualmente los pasos a continuación. Caso contrario puedes [ir hacia adelante a la sección para ejecutar el chaincode](#ejecuta-el-chaincode-localmente).

Los paquetes de chaincode en Fabric son archivos en formato `tgz` que contienen dos archivos:

- `metadata.json` - la etiqueta y tipo del chaincode
- `code.tar.gz` - artefactos fuentes del chaincode

Crea el archivo `metadata.json` primero, esto le indica al Peer el tipo de chaincode y la etiqueta a usar para referirse a él mas adelante.

```bash
cat << METADATAJSON-EOF > metadata.json
{
    "type": "ccaas",
    "label": "asset-transfer"
}
METADATAJSON-EOF
```

Crea el archivo `code.tar.gz` - para el Chaincode-as-a-service, este archivo contendrá un solo archivo JSON `connection.json`. El proceso de empaquetar tradicional de Fabric incluiría aquí todo el código fuente del chaincode. En este caso, necesitamos que el archivo JSON contenga el URL donde el peer encontrará el chaincode y un limite de tiempo de espera. Note que este es un hostname especial para que el peer dentro del contenedor docker pueda localizar al chaincode ejecutándose en el sistema host.

```
cat << CONNECTIONJSON-EOF > connection.json
{
  "address":"host.docker.internal:9999",
  "dial_timeout":"15s"
}
CONNECTIONJSON-EOF
```

Podemos ahora construir el paquete real. Crea un archivo code.tar.gz que contendrá el archivo connection.json.

```bash
tar -czf code.tar.gz connection.json
```

Crea el archivo empaquetado final del chaincode.

```bash
tar -czf asset-transfer.tgz metadata.json code.tar.gz
```

Utilizaremos los comandos CLI del peer para instalar e implementar el chaincode. Éste estará 'implementado' cuando se indique acuerdo para hacerlo y luego se asigne a un channel:

```
source _cfg/uf/org1admin.env

peer lifecycle chaincode install asset-transfer.tgz
```

El ChaincodeID que es devuelto por este comando de instalación debe ser almacenado, típicamente esto es mejor como una variable de entorno.

```bash
export CHAINCODE_ID=$(peer lifecycle chaincode calculatepackageid asset-transfer.tgz)
```

Paso siguiente, define el chaincode en el channel de blockchain aprobándolo y asignándoselo. Si ya lo has implementado utilizando la receta `just` de mas arriba, debes incrementar el número de `--sequence` a `2`.

```bash
peer lifecycle chaincode approveformyorg --channelID mychannel --name asset-transfer -v 0 --package-id $CHAINCODE_ID --sequence 2 --connTimeout 15s
peer lifecycle chaincode commit --channelID mychannel --name asset-transfer -v 0 --sequence 2 --connTimeout 15s
```

## Ejecuta el chaincode localmente

Utilizaremos el contrato ejemplo en typescript ya escrito en `$WORKSHOP_PATH/contracts/asset-transfer-typescript`. Siéntete libre de revisar el código del contrato en [contracts/asset-transfer-typescript/src/assetTransfer.ts](../../contracts/asset-transfer-typescript/src/assetTransfer.ts). Podrás observar allí la implementación de funciones del contrato tales como `CreateAsset()` y `ReadAsset()`.

Utiliza otra ventana de terminal para el chaincode. Asegúrate que la terminal este definida con las mismas variables de entorno como en la primera terminal:

```
cd fabric-samples/full-stack-asset-transfer-guide
export WORKSHOP_PATH=$(pwd)
export PATH=${WORKSHOP_PATH}/bin:$PATH
export FABRIC_CFG_PATH=${WORKSHOP_PATH}/config
```

Como con cualquier módulo en typescript debemos ejecutar `npm install` para administrar las dependencias del chaincode y luego construir (compilar) el código typescript del chaincode a javascript.

```
cd contracts/asset-transfer-typescript

npm install

npm run build
```

Una forma fácil de verificar que el contrato ha sido construido correctamente es generar la metadata del contrato ('Contract Metadata') en un archivo `metadata.json`. Esta es una definición del contrato y de los tipos de datos que éste retornó que es agnóstica a lenguajes. Toma prestado conceptos de OpenAPI utilizados en la definición de REST APIs.  Es también muy útil para compartir con equipos que están escribiendo aplicaciones cliente para que conozcan la estructura de la data y las funciones de las transacciones que pueden invocar.
Como es un documento JSON, puede ser utilizado para crear otros recursos.

El comando que genera la metadata ha sido colocado en el `package.json`:

```
npm run metadata
```

Revisa el archivo `metadata.json` generado y observa el resumen de la información del contrato, las funciones de transacciones y los tipos de datos. Esta información puede ser obtenida también en el momento de ejecución y es una buena forma de probar la implementación.


## Desarrollo Iterativo y Pruebas

**Todos los pasos hasta ahora han sido por única vez. De aquí en adelante puedes iterar sobre el desarrollo de tu contrato**

Comencemos el módulo de tu nodo de Contrato Inteligente desde la ventana de terminal de tu contrato. Recuerda que el `CHAINCODE_ID` y el `CHAINCODE_SERVER_ADDRESS` son las únicas piezas de información que se necesita.

Nota: Usa tu específico CHAINCODE_ID obtenido anteriormente; el `CHAINCODE_SERVER_ADDRESS` es diferente - esto es porque en este caso lo esta indicando al chaincode donde escuchar a conexiones entrantes desde el Peer. Usaremos el puerto 9999 de la maquina local.

```
source ${WORKSHOP_PATH}/_cfg/uf/org1admin.env

# si ejecutaste el justfile de arriba, estos valores ya estarán exportados, pero puedes querer verificar que tengan los siguientes valores:
export CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999
export CHAINCODE_ID=$(peer lifecycle chaincode calculatepackageid asset-transfer.tgz)

npm run start:server-debug
```

### Ejecutar algunas transacciones

Elije una ventana de terminal desde donde ejecutar las transacciones; inicialmente utilizaremos el CLI del `peer` para ejecutar los comandos.

Si es una nueva ventana de terminal configura las variables de entorno:

```
cd fabric-samples/full-stack-asset-transfer-guide
export WORKSHOP_PATH=$(pwd)
export PATH=${WORKSHOP_PATH}/bin:$PATH
export FABRIC_CFG_PATH=${WORKSHOP_PATH}/config
```

Asegúrate que tanto el binario del peer como el directorio config estén configurados (ejecuta el script `${WORKSHOP_PATH}/check.sh` para verificar).

Configura el contexto de ambiente para hacer de Administrador Org 1.

```
source ${WORKSHOP_PATH}/_cfg/uf/org1admin.env
```

Usa el CLI del peer para ejecutar comandos básicos de consulta contra el contrato. Por ejemplo, revisar la metadata del contrato (si tienes jq, es mas fácil leer si haces pipe de los resultados a jq). Usa uno de estos comandos:

```
peer chaincode query -C mychannel -n asset-transfer -c '{"Args":["org.hyperledger.fabric:GetMetadata"]}'
peer chaincode query -C mychannel -n asset-transfer -c '{"Args":["org.hyperledger.fabric:GetMetadata"]}' | jq
```

Creemos un activo con ID=001:

```
peer chaincode invoke -C mychannel -n asset-transfer -c '{"Args":["CreateAsset","{\"ID\":\"001\", \"Color\":\"Red\",\"Size\":52,\"Owner\":\"Fred\",\"AppraisedValue\":234234}"]}' --connTimeout 15s
```

Si estas observando los logs de MicroFab veras que el peer grabo un nuevo bloque al ledger.

Procede ahora a leer ese activo:

```
peer chaincode query -C mychannel -n asset-transfer -c '{"Args":["ReadAsset","001"]}'
```

Veras que el activo es regresado:

```
{"AppraisedValue":234234,"Color":"Red","ID":"001","Owner":"{\"org\":\"org1MSP\",\"user\":\"Fred\"}","Size":52}
```

### Hacer un cambio y re-ejecutar el código

Si invocamos un comando de consulta en un activo que no existe, por ejemplo 002, obtendremos un error:

```
peer chaincode query -C mychannel -n asset-transfer -c '{"Args":["ReadAsset","002"]}'
```

da un error:

```
Error: endorsement failure during query. response: status:500 message:"Sorry, asset 002 has not been created"
```

Supongamos que queremos cambiar ese mensaje de error por otra cosa.

- Detener el chaincode que se esta ejecutando (CTRL-C en la terminal del chaincode)
- Abrir el archivo `src/assetTransfer.ts` en el editor de tu preferencia
- Alrededor de la linea 51, encuentra el string del error y haz una modificación. Recuerda guardar el cambio.
- Compila nuevamente el contrato en typescript:
```
npm run build
```

Puedes arrancar nuevamente el contrato como hiciste antes

```
npm run start:server-debug
```


Y ejecutar la misma consulta visualizando el mensaje de error actualizado:

```
peer chaincode query -C mychannel -n asset-transfer -c '{"Args":["ReadAsset","002"]}'
```

## Depurar el código

Como el chaincode fue iniciado con la configuración de depuración de Node.js, se puede conectar al proceso un depurador de node.js. Por example VSCode trae un buen depurador de typescript/node.js.

Si seleccionas el tab de depurar, y abres las configuraciones de depuración, agrega la configuración "Asociar al proceso node".
VSCode te mostrara el modelo. El puerto por defecto debería ser suficiente.
Puedes entonces arrancar la depuración 'asociada al proceso', y seleccionar el proceso que se quiere depurar.

Recuerda establecer un punto de quiebre al comienzo de la función transaccional que quieres depurar.

Presta atención a:
    - VSCode usa node, así que ten cuidado para seleccionar el proceso correcto
    - recuerda que existe un límite de tiempo de la transacción cliente/fabric, mientras tengas al chaincode detenido en el depurador, el límite de tiempo sigue 'contando'


Revisa [Probar y Depurar Contratos](./03-Test-And-Debug-Reference-ES.md) para mas detalles e información en otros lenguajes.
