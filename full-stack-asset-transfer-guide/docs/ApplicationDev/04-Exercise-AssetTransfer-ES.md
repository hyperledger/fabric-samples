# Ejercicio: Implementar transferencia de activos

Actualmente, nuestra aplicación de comerciante solo puede crear, leer y eliminar activos invocando las funciones de chaincode CreateAsset(), ReadAsset(), and DeleteAsset(). Para ser realmente útil, necesita poder transferir activos a nuevos propietarios invocando la función de chaincode TransferAsset().

Ya existe un comando **transfer** implementado en [transfer.ts](../../applications/trader-typescript/src/commands/transfer.ts), que invoca al método `transferAsset()` en nuestra clase **AssetTransfer**. Desafortundamente, esto no ha sido implementado aún y no hace nada.

1. Escribe una implementación para el método `transferAsset()` en [contract.ts](../../applications/trader-typescript/src/contract.ts). Revisa la [documentación de la API para Contratos](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Contract.html) y los otros métodos en la clase **AssetTransfer** para ideas de como seguir.

2. Recompila la aplicación de tu TypeScript actualizado:
    ```bash
    npm install
    ```
    > **Consejo:** Puedes dejar también ejecutándose `npm run build:watch` en una ventana de terminal para reconstruir automáticamente tu aplicación ante cualquier cambio en el código.

3. Si estas utilizando una nueva ventana de terminal, recuerda definir las variables de entorno para que apunten a los recursos requeridos por la aplicación.
    ```bash
    export ENDPOINT=org1peer-api.127-0-0-1.nip.io:8080
    export MSP_ID=org1MSP
    export CERTIFICATE=../../_cfg/uf/_msp/org1/org1admin/msp/signcerts/cert.pem
    export PRIVATE_KEY=../../_cfg/uf/_msp/org1/org1admin/msp/keystore/cert_sk
    ```

4. ¡Pruébalo! Utiliza el comando **transfer** para transferir activos a nuevos propietarios con el mismo ID de MSP.

5. ¿Qué sucede si intentas manipular (transferir, eliminar) un activo después de transferirlo a otro ID de MSP?

El contrato inteligente contiene lógica que solo permite a los usuarios de la organización propietaria modificar activos. Esto se logra verificando que el ID del Proveedor de Servicios de Membresía (MSP) de la identidad del cliente que invoca la transacción coincida con el ID de MSP de la organización propietaria del activo. Si no habías notado esto antes, quizás quieras revisar el código del contrato inteligente para ver cómo se implementa.

## Pasos Opcionales

Implementar un comando **update** en la aplicación cliente que permita modificar propiedades de un activo.
