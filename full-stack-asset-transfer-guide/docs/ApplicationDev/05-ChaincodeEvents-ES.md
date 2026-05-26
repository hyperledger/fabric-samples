# Eventos de Chaincode

Una función de transacción de un contrato inteligente puede emitir un evento de chaincode para comunicar eventos de negocio. Estos eventos se emiten solo después de que una transacción se confirma con éxito y actualiza el ledger. Las transacciones que no pasan la validación no emiten eventos de chaincode.

Las aplicaciones cliente pueden escuchar eventos de chaincode y desencadenar procesos de negocio externos en respuesta a actualizaciones del ledger. Un ejemplo podría ser programar la recogida de un paquete después de recibir una orden de entrega. Los eventos pueden reproducirse desde cualquier punto en la blockchain o recibirse en tiempo real.

Al emitir un evento de chaincode, el contrato inteligente puede especificar un **payload** arbitrario para incluir en el evento. El **payload** se utiliza para comunicar el contexto de negocio a las aplicaciones cliente que reciben los eventos de chaincode.

El objeto de [Red](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Network.html) en la API de Gateway provee métodos para obtener los eventos de chaincode.

Para garantizar el correcto funcionamiento de los procesos de negocio, es importante que cada evento de chaincode se reciba exactamente una vez. ¡No queremos recoger el mismo paquete dos veces ni dejar de recoger un paquete!

La API de Gateway permite usar un [Checkpointer](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Checkpointer.html) para rastrear (o marcar) los eventos procesados con éxito, y reanudar los eventos exactamente después del último evento marcado si ocurre un fallo o un reinicio de la aplicación.

Para mayor comodidad, la API de Gateway proporciona dos implementaciones de checkpointer:

1. [Checkpointer de Archivo](https://hyperledger.github.io/fabric-gateway/main/api/node/functions/checkpointers.file.html) that persists its state to the file-system. This can be used to resume eventing, even after an application restart.
2. [Checkpointer en memoria](https://hyperledger.github.io/fabric-gateway/main/api/node/functions/checkpointers.inMemory.html) that stores its state only in-memory. This can be used to recover from transient failures, such as a network communication error, during a single application run.

Las aplicaciones cliente también pueden utilizar sus propias implementaciones de checkpointer, que persisten su estado en un almacenamiento adecuado, como una base de datos, siempre que cumplan con la interfaz simple de [Checkpoint](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Checkpoint.html).
