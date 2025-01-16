# Ejercicio: Usar eventos de chaincode

Primero, intentemos escuchar los eventos de chaincode para ver qué información se incluye en los eventos emitidos por las funciones de la transacción del contrato inteligente.

En una nueva ventana de terminal, navega al directorio [applications/trader-typescript](../../applications/trader-typescript/) para que puedas ejecutar a la aplicación de escucha.
Se asume que ya has compilado la aplicación en pasos previos.

1. Si estas utilizando una nueva ventana de terminal, define a las variables de entorno para que apunten a las recursos requeridos por la aplicación.
    ```bash
    export ENDPOINT=org1peer-api.127-0-0-1.nip.io:8080
    export MSP_ID=org1MSP
    export CERTIFICATE=../../_cfg/uf/_msp/org1/org1admin/msp/signcerts/cert.pem
    export PRIVATE_KEY=../../_cfg/uf/_msp/org1/org1admin/msp/keystore/cert_sk
    ```

2. Ejecuta el comando **listen** para escuchar las actualizaciones del ledger. El comando listen retornará eventos previos y también esperará por eventos futuros.
    ```bash
    npm start listen
    ```

3. Una vez que hayas recibido los eventos disponibles, interrumpe la aplicación utilizando `Control-C`.

4. Ejecuta el comando **listen** nuevamente. ¿Qué es lo que observas esta vez?

En la segunda corrida del comando **listen**, deberías haber observado exactamente la misma salida que en la primera ejecución. Esto se debe a que cada ejecución del comando **listen** recupera todos los eventos de chaincode desde el inicio de la blockchain. Eso no es muy útil si queremos invocar procesos de negocio externos en respuesta a eventos de chaincode. Sería mucho mejor si cada evento se recibiera exactamente una vez, independientemente de si la aplicación cliente se reinicia..

Implementemos el checkpointing para asegurarnos de que no haya eventos duplicados ni perdidos.

5. Implementa checkpointing para la lectura de eventos de chaincode en [listen.ts](../../applications/trader-typescript/src/commands/listen.ts). Revisa la [documentación de la API de Red](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Network.html) para obtener ideas de como proceder. ¡Asegúrate de marcar los eventos como checkpoint solo *después* de que se hayan procesado con éxito!

6. Después de asegurarte que tus cambios fueron compilados, ejecuta el comando **listen** con la variable de entorno SIMULATED_FAILURE_COUNT seteada para simular un error aplicativo durante el procesamiento de un evento de chaincode:
    ```bash
    SIMULATED_FAILURE_COUNT=3 npm start listen
    ```

7. Ejecuta el comando **listen** nuevamente. Deberías ver que la escucha de eventos se reanuda desde el mismo evento de chaincode que la aplicación no pudo procesar en la ejecución anterior.

> **Nota:** El checkpointer guarda su posición actual de escucha en un archivo `checkpoint.json`. Si deseas eliminar el estado almacenado del checkpointer y comenzar a escuchar desde el bloque definido en `startBlock` nuevamente, elimina el archivo `checkpoint.json` mientras el checkpointer no esté en uso.

## Pasos Opcionales

Hasta ahora hemos estado reproduciendo eventos de chaincode emitidos previamente. Usemos el comando **listen** para notificarnos en tiempo real cuando tomemos posesión de activos.

8. Modifica la función **onEvent()** en [listen.ts](../../applications/trader-typescript/src/commands/listen.ts) para notificarte cuando pases a ser el propietario de un activo nuevo (evento `CreateAsset`) o transferido (evento `TransferAsset`). Ten en cuenta que la propiedad `payload` del evento es un [Uint8Array](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Uint8Array) que contiene el [JSON](https://en.wikipedia.org/wiki/JSON) emitido por el contrato inteligente. Consulta el método **readAsset()** en [contract.ts](../../applications/trader-typescript/src/contract.ts) para obtener ideas sobre cómo convertir esto en un objeto JavaScript y poder inspeccionar su propiedad `Owner`.

9. Intenta ejecutar el comando **listen** en una ventana de terminal mientras utilizas otra ventana de terminal para crear y transferir activos.
