# Ejercicio: Ejecutar la aplicación cliente

> **Nota:** Este ejercicio requiere que la red de Fabric y el chaincode desplegado en los ejercicios de [Desarrollo de Contratos Inteligentes](../SmartContractDev/) estén en funcionamiento.

Asegurémonos de que podemos ejecutar correctamente la aplicación cliente y familiarizarnos con su uso.

En una ventana de terminal, navega al directorio [applications/trader-typescript](../../applications/trader-typescript/). Luego completa los siguientes pasos:

1. Instalar dependencias y compilar la aplicación cliente.
    ```bash
    npm install
    ```

1. Definir variables de entorno que apuntan a recursos requeridos por la aplicación.
    ```bash
    export ENDPOINT=org1peer-api.127-0-0-1.nip.io:8080
    export MSP_ID=org1MSP
    export CERTIFICATE=../../_cfg/uf/_msp/org1/org1admin/msp/signcerts/cert.pem
    export PRIVATE_KEY=../../_cfg/uf/_msp/org1/org1admin/msp/keystore/cert_sk
    ```

1. Ejecutar el comando **getAllAssets** para verificar que activos existen actualmente en el ledger (si hay alguno).
    ```bash
    npm start getAllAssets
    ```

1. Ejecutar el comando **transact** para crear (y actualizar / eliminar) algunos activos ejemplos adicionales.
    ```bash
    npm start transact
    ```

1. Ejecutar el comando **getAllAssets** de nuevo para ver los nuevos activos registrados en el ledger.
    ```bash
    npm start getAllAssets
    ```

Estos comandos de aplicación CLI representan una aplicación simplificada que realiza una acción por llamada. Tenga en cuenta que las aplicaciones del mundo real generalmente serán de larga duración y realizarán llamadas a un contrato de parte de las solicitudes de los usuarios.

## Pasos Opcionales

Intenta utilizar los comandos **create**, **read** y **delete** para trabajar con activos específicos.

Ver el [Readme](../../applications/trader-typescript/README.md) de la aplicación para detalles de cómo usar los comandos.
