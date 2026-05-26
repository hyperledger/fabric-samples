# Referencia para Probar y Depurar

[ANTERIOR - Agregar una Función Transaccional](./02-Exercise-Adding-tx-function-ES.md)

**Meta:** Iniciar un Contrato Inteligente en Hyperledger Fabric para que pueda ser depurado fácilmente

**Objetivos:**

- Introducir qué es Chaincode-as-a-Service, y cómo ayuda
- Mostrar cómo construir y configurar un Chaincode para ejecutar de esta manera
- Cómo se implementan estos en una red que esta levantada de Hyperledger Fabric
- Cómo depurar este Chaincode que se está ejecutando.

---

## Resumen

Ayuda pensar en estas tres 'partes':

- La red de Fabric, que consiste de los peers, ordenadores, autoridades certificantes etc. Junto con los channels configurados y las identidades.
  Para nuestros propósitos aquí, esto puede ser considerado como una 'caja negra'. La 'caja negra' puede ser configurada de maneras diferentes, pero típicamente será uno o mas contenedores docker. Este workshop usa MicroFab para levantar la red de Fabric en un único contenedor docker.
- El Chaincode - que estará ejecutándose en su propio proceso o contenedor docker.
- El editor - VSCode es usado aquí, pero el enfoque debería ser el mismo con otros depuradores y editores.

El _proceso a alto nivel_ es

0. Levantar Fabric
1. Desarrollar el Contrato Inteligente
3. Crear un paquete de chaincode utilizando el enfoque de chaincode-as-a-service
4. Instalar el chaincode en un peer y Aprobar/Asignar el chaincode a un channel
5. Iniciar el chaincode utilizando el enfoque chaincode-as-a-service
6. Anexar el depurador al chaincode ejecutándose y definir un punto de quiebre
7. Invocar una transacción, ésta se detendrá entonces en el depurador para que puedas pasar sobre el código
8. Encontrar los errores y repetir **desde paso 5** - ten en cuenta que no necesitamos Empaquetar/Instalar/Aprobar/Asignar de nuevo el chaincode.

Este es el proceso exacto que habrás seguido en la sección ['Comenzar'](./01-Exercise-Getting-Started-ES.md).

### ¿Qué necesitas?

Necesitaras contar con disponibilidad de docker, junto con VSCode. Además instala las extensiones de VSCode que prefieres para depurar tu lenguaje de programación preferido. Existen otros depuradores disponibles y eres libre de usarlos si cuentas con ellos.

- Para TypeScript y JavaScript VSCode tiene el soporte incorporado
- Para Java es sugerido el [paquete JavaExtension](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack).

### ¿Qué es Chaincode como un Servicio?

La facilidad de chaincode-as-a-service es una manera muy práctica y útil para ejecutar 'Contratos Inteligentes'. Tradicionalmente ha sido el Fabric Peer el que ha tomado el role de orquestar el ciclo de vida completo del chaincode. Esto requería acceso al Daemon de Docker para crear imágenes, e iniciar contenedores. Los marcos de chaincode programados en Java, Node.js y Go eran explícitamente conocidos por el peer incluyendo cómo debían ser compilados e iniciados.

Como resultado, esto hacía difícil implementar en entornos de estilo Kubernetes (K8S) o ejecutar de cualquier manera en modo depuración. Adicionalmente, el código esta siendo re compilado por el peer entonces existe cierto grado de incertidumbre respecto de qué dependencias deben ser incorporadas.

Hacer uso de chaincode-as-service requiere que sea uno quien orqueste la fase de compilación e implementación. Aunque esto es un paso adicional, devuelve el control. El Peer aún requiere que un 'paquete de chaincode' sea instalado. En este caso, éste no contiene código, pero si la información de dónde el chaincode esta hospedado (Hostname, Puerto, config de TLS, etc).


## Ejecutar los Contratos Inteligentes

Un punto importante es que el código escrito para el Contrato Inteligente sea el mismo, sea que esté administrado por el peer o como Chaincode-as-a-Service.
Lo que difiere es como es iniciado y empaquetado. El proceso global es el mismo, sin importar si el contrato inteligente este escrito en Java/Typescript/Go.

### TypeScript/JavaScript

Usando el contrato en Typescript como un ejemplo, se puede visualizar mejor la diferencia. El package.json contiene 4 comandos de 'start'.

```
   "start": "fabric-chaincode-node start",
   "start:server-nontls": "fabric-chaincode-node server --chaincode-address=$CHAINCODE_SERVER_ADDRESS --chaincode-id=$CHAINCODE_ID",
   "start:server": "fabric-chaincode-node server --chaincode-address=$CHAINCODE_SERVER_ADDRESS --chaincode-id=$CHAINCODE_ID --chaincode-tls-key-file=/hyperledger/privatekey.pem --chaincode-tls-client-cacert-file=/hyperledger/rootcert.pem --chaincode-tls-cert-file=/hyperledger/cert.pem",
   "start:server-debug": "set -x && NODE_OPTIONS='--inspect=0.0.0.0:9229' fabric-chaincode-node server --chaincode-address=$CHAINCODE_SERVER_ADDRESS --chaincode-id=$CHAINCODE_ID"
```

El primero es usado cuando el peer es quien controla completamente el chaincode. El segundo `start:server-nontls` se inicia en modo Chaincode-as-a-service (sin usar TLS). El comando es muy similar a `fabric-chaincode-node server` mas que `fabric-chaincode-node start`. Se proveen dos opciones aquí, que son, la dirección de red donde estará escuchando el chaincode y su id (aparte de cuando el Peer ejecuta el chaincode, si pasa opciones extras, pero no se pueden ver en el package.json).

El tercer `start:server` agrega la configuración TLS requerida, pero fuera de esto es igual.
El cuarto `start:server-debug` es igual al caso de no-TLS, pero incluye la variable de entorno necesaria para que Node.js abra un puerto para permitirle al depurador conectarse remotamente.

### Java

Los cambios para los chaincode en Java son lógicamente los mismos. El build.gradle (o si deseas usa Maven) es exactamente igual (como si no hubiese cambios en la compilación de
TypeScript). Con las librerías v2.4.1  de Chaincode en Java, no hay cambios en el código para hacer o compilar cambios. El modo '-as-a-service' será usado si esta definida la variable de entorno `CHAINCODE_SERVER_ADDRESS`.

Para el caso no-TLS el chaincode en Java es iniciado con `java -jar /chaincode.jar` - y usará el modo Chaincode-as-a-service _si_ esta definida la variable de entorno `CHAINCODE_SERVER_ADDRESS`.

Para depurar, la JVM necesita ser colocada en modo depuración `java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0.0.0.0:8000 -jar /chaincode.jar`

## ¿En qué se diferencia el paquete de chaincode?

Una diferencia clave es que el paquete de chaincode no contiene código. Es utilizado como un contenedor de data que le indica al peer donde se encuentra el chaincode.
¿Qué host/port y qué configuración TLS se necesitan? Los paquetes de chaincode ya contienen data respecto de los indices de CouchDB a usar o las colecciones de data privada.

Dentro del paquete, el archivo `connection.json` es importante. En su expresión mas simple sería:

```json
{
  "address": "assettransfer_ccaas:9999",
  "dial_timeout": "10s",
  "tls_required": false
}
```

Esto le esta indicando al peer que el chaincode esta en el host `assettransfer_ccaas` puerto 9999. Define 10s como el tiempo de espera para conectarse y que TLS no es requerido.

El paquete puede ser construido manualmente, es un conjunto de archivos json, que se pueden juntar con `tgz`.

### Importante aviso de interconexión

El paquete del chaincode que es instalado críticamente contiene el nombre de red y puerto donde el peer espera que el chaincode este escuchando. Si nada contesta al peer, la transacción obviamente fallará.

Ten en cuenta que esta bien que el chaincode no se este ejecutando todo el tiempo, el peer no se quejará hasta que le sea efectivamente pedido conectarse al chaincode. Esta es una facilidad importante ya que permite que se pueda depurar y re iniciar el contenedor.

El nombre de red que es suministrado debe ser algo que el peer, desde su perspectiva, pueda resolver. Típicamente el peer se encontrará dentro de un contenedor docker, entonces proporcionar `localhost` o `127.0.0.1` resolverá al mismo contenedor donde esta ejecutándose el peer..

Asumiendo que el peer esta ejecutándose en un contenedor docker, el chaincode podría estar corriendo en su propio contenedor docker en la misma red de docker que el contenedor del peer, o podría estar ejecutándose directamente en el sistema host.

Dependiendo del sistema operativo de tu host, el 'specialhostname' que es usado desde dentro del contenedor docker para acceder al host varía.
 Por ejemplo, mira esto [stackoverflow post](https://stackoverflow.com/questions/24319662/from-inside-of-a-docker-container-how-do-i-connect-to-the-localhost-of-the-mach#:~:text=To%20access%20host%20machine%20from,using%20it%20to%20anything%20else.&text=Then%20make%20sure%20that%20you,0.0%20.)

La ventaja de esto es que el chaincode puede ejecutarse localmente en tu maquina host y es fácil conectarse al mismo desde un depurador.

Alternativamente, puedes empaquetar el chaincode en su propio contenedor docker, y ejecutar eso. Aún puedes conectarte para depurar pero debes asegurarte que los puertos del contenedor esten expuestos correctamente para el ambiente de ejecución de tu lenguaje.

## Paso individual y tiempos de espera

- Si vas a depurar paso a paso, entonces probablemente te topes con el valor del tiempo de espera de la transacción de Fabric. Por defecto este valor es de 30 segundos, que significa que el chaincode debe completar la transacción en 30 segundos o menos antes de que el peer limite el tiempo del requerimiento. En tu `config/core.yaml` actualiza `executetimeout` a `300s`, o agrega `CORE_CHAINCODE_EXECUTETIMEOUT=300s` a las variables de entorno de cada peer, para que puedas pasar por el código de tu contrato en un depurador por 5 minutos por función transaccional invocada.
