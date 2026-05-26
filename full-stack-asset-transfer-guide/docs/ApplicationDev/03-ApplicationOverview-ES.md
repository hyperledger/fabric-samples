# Descripción General de la Aplicación

Este tema describe las partes clave de la aplicación cliente y cómo utiliza la API del cliente Fabric Gateway para interactuar con la red. Este conocimiento le permitirá extender la aplicación en los temas siguientes.

## Conectarse al Servicio Gateway

La conexión al servicio peer Gateway está controlada por la función **runCommand()** en [app.ts](../../applications/trader-typescript/src/app.ts). Esta llama a otras dos funciones para realizar las dos tareas necesarias antes de que la aplicación cliente pueda realizar transacciones con la red Fabric:

1. **Crear conexión gRPC al punto de entrada del peer Gateway** - esto es realizado en la función **newGrpcConnection()** en [connect.ts](../../applications/trader-typescript/src/connect.ts):
    ```typescript
    const tlsCredentials = grpc.credentials.createSsl(tlsRootCert);
    return new grpc.Client(GATEWAY_ENDPOINT, tlsCredentials);
    ```
    La conexión cliente gRPC es establecida utilizando el [gRPC API](https://grpc.io/docs/) y es manejada por la aplicación cliente. La aplicación puede utilizar la misma conexión gRPC connection para transaccionar en nombre de muchas identidades de clientes.

1. **Crear conexión con peer Gateway** - esto es realizado en la función **newGatewayConnection()** en [connect.ts](../../applications/trader-typescript/src/connect.ts):
    ```typescript
    return connect({
        client,
        identity: await newIdentity(),
        signer: await newSigner(),
        // Default timeouts for different gRPC calls
        evaluateOptions: () => {
            return { deadline: Date.now() + 5000 }; // 5 seconds
        },
        endorseOptions: () => {
            return { deadline: Date.now() + 15000 }; // 15 seconds
        },
        submitOptions: () => {
            return { deadline: Date.now() + 5000 }; // 5 seconds
        },
        commitStatusOptions: () => {
            return { deadline: Date.now() + 60000 }; // 1 minute
        },
    });
    ```
    La conexión **Gateway** es establecida invocando la función de factoría [connect()](https://hyperledger.github.io/fabric-gateway/main/api/node/functions/connect.html) con una [identidad](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Identity.html) cliente (certificado X.509 del usuario) y una [implementación de firma](https://hyperledger.github.io/fabric-gateway/main/api/node/functions/signers.newPrivateKeySigner.html) (basado en la llave privada del usuario). Le permite a un usuario especifico interactuar con una red Fabric utilizando la conexión gRPC creada previamente. Una configuración opcional también puede ser suministrada, y es muy recomendado incluir tiempos de espera por defecto para las operaciones.

## Comandos de Aplicación CLI

Todas las implementaciones de los comandos CLI están ubicados dentro del directorio [commands](../../applications/trader-typescript/src/commands/). Los comandos son expuestos a [app.ts](../../applications/trader-typescript/src/app.ts) por [commands/index.ts](../../applications/trader-typescript/src/commands/index.ts).

Cuando es invocado, el comando recibe la instancia **Gateway** que debe utilizar para interactuar con la red Fabric. Para hacer un trabajo útil, la implementación de los comandos típicamente realizan estos pasos:

1. **Obtener una Red** - esto representa una red de nodos Fabric que perteneces a un canal específico de Fabric:
    ```typescript
    const network = gateway.getNetwork(CHANNEL_NAME);
    ```

1. **Obtener un Contrato** - esto representa un contrato inteligente específico desplegado en la **Red**:
    ```typescript
    const contract = network.getContract(CHAINCODE_NAME);
    ```

1. **Crear un adaptador del contrato inteligente** - esto provee una vista del contrato inteligente y las funciones de la transacción de una manera que es fácil de usar para la lógica de negocio de la aplicación cliente:
    ```typescript
    const smartContract = new AssetTransfer(contract);
    ```

1. **Invocar las funciones de la transacción en un chaincode desplegado** - por ejemplo:
    - Crear un activo en [commands/create.ts](../../applications/trader-typescript/src/commands/create.ts)
        ```typescript
        await smartContract.createAsset({
            ID: assetId,
            Owner: owner,
            Color: color,
            Size: 1,
            AppraisedValue: 1,
        });
        ```
    - Leer todos los activos en [commands/getAllAssets.ts](../../applications/trader-typescript/src/commands/getAllAssets.ts)
        ```typescript
        const assets = await smartContract.getAllAssets();
        ```

Los comandos CLI de la aplicación representan una aplicación simplificada que realiza una acción por llamada. Tenga en cuenta que las aplicaciones del mundo real generalmente serán de larga duración y reutilizarán una conexión al servicio peer Gateway cuando realicen solicitudes de transacciones en nombre de las aplicaciones cliente. La conexión puede utilizar una única identidad de organización en nombre de varias solicitudes de usuario.

## Invocaciones al Gateway API 

La clase **AssetTransfer** en [contract.ts](../../applications/trader-typescript/src/contract.ts) presenta al contrato inteligente en un formato apropiado para la aplicación de negocio. Internamente, utiliza la API del cliente Fabric Gateway para invocar funciones de transacción y se encarga de la traducción entre la aplicación de negocio y la representación de parámetros y valores de retorno de la API.

Para mas detalles de las invocaciones disponibles se puede consultar la [documentación de la API de Contratos](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Contract.html).

### Transacción submit

La función de submit de transacciones enviará la solicitud al servicio peer Gateway. El servicio peer Gateway invocará el chaincode y recopilará los endosos necesarios de los peers de diferentes organizaciones para cumplir con la política de endosos del contrato. Luego enviará la transacción al servicio de ordenamiento en nombre de la aplicación cliente, para que el ledger de blockchain pueda ser actualizado.

Un ejemplo de envío de transacciones se encuentra en el método **createAsset()**:

```typescript
await this.#contract.submit('CreateAsset', {
    arguments: [JSON.stringify(asset)],
});
```

### Transacción evaluate

La función de evaluación de transacciones solicitará al servicio peer Gateway que invoque el chaincode y devuelva los resultados al cliente, sin enviar una transacción al servicio de ordenamiento. Utilice la función de evaluación para consultar el estado del ledger de blockchain.

Un ejemplo de evaluación de una transacción se encuentra en el método **getAllAssets()**:
```typescript
const result = await this.#contract.evaluate('GetAllAssets');
```

## Reintentos de la transacción submit

La naturaleza del flujo de envío de transacciones en Fabric implica que pueden ocurrir fallos en diferentes puntos del flujo. Para ayudar al cliente a gestionar los fallos, la API de Gateway genera errores de tipos específicos para indicar el punto en el flujo donde ocurrió el fallo. La función **submitWithRetry()** en [contract.ts](../../applications/trader-typescript/src/contract.ts) reintenta las transacciones que no logran confirmarse exitosamente:

```typescript
let lastError: unknown | undefined;

for (let retryCount = 0; retryCount < RETRIES; retryCount++) {
    try {
        return await submit();
    } catch (err: unknown) {
        lastError = err;
        if (err instanceof CommitError) {
            // Transaction failed validation and did not update the ledger. Handle specific transaction validation codes.
            if (err.code === StatusCode.MVCC_READ_CONFLICT) {
                continue; // Retry
            }
        }
        break; // Failure -- don't retry
    }
}

throw lastError;
```

Ver la [documentación de la API de submit()](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Contract.html#submit) para conocer los otros tipos de errores que puede arrojar.

Para algunos casos puede ser más útil reintentar solo un paso especifico dentro del flujo de envío de transacciones. La API de Gateway proporciona un flujo detallado para permitir esto. Ver la [documentación de la API de Contratos](https://hyperledger.github.io/fabric-gateway/main/api/node/interfaces/Contract.html) para encontrar ejemplos de este flujo detallado.
