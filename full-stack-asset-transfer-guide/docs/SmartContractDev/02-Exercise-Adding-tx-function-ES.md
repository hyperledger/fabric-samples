## Agregar una Función Transaccional

[ANTERIOR - Comenzar](./01-Exercise-Getting-Started-ES.md) <==>  [SIGUIENTE - Probar y Depurar](./03-Test-And-Debug-Reference-ES.md)

 En este ejercicio, agregaremos una función transaccional para verificar el valor estimado. Escribiremos esta función como parte del ciclo de desarrollo iterativo de un chaincode-as-a-service externo, por lo cual no hay un requerimiento de detener Fabric o preocuparnos por las versiones implementadas del chaincode. Simplemente estaremos actualizando el código fuente del chaincode, reiniciando el servicio del chaincode, y probando la nueva función.

La función será:

- una función de 'solo-lectura'
- esperará un id del activo y un valor superior e inferior
- regresará una indicación por verdadero/false si el valor estimado se encuentra dentro de los valores superior e inferior

## Pasos

Asegúrate primero que has ejecutado el Contrato Inteligente y podido emitir transacciones contra él. Vale la pena cerciorarse que puedes detener y reiniciar el código luego de hacer cambios menores.

- En el archivo `assetTransfer.ts` crea una nueva función `ValidateValue`. La función `ReadAsset` es una buena función para usar como una base. Es una función de solo-lectura y ya obtiene el activo del ledger.
-  Agrega un valor superior e inferior a los parámetros de la función.
- La función`ReadAsset` regresa directamente el activo, puedes mirar la función`UpdateAsset` para ver cómo procesar la data.
- Verifica el valor y regresa verdadero/false dependiendo si el valor esta entre los límites o no.
- Si quieres también puedes establecer un evento.

Recuerda detener el código que está ejecutándose, compilarlo e iniciarlo nuevamente. Recuerda que puedes anexar el depurador para ayudar a identificar los problemas.

## Probar

Puedes entonces invocar este código con comandos similares como en [Comenzar](./01-Exercise-Getting-Started-ES.md).

Por ejemplo para verificar que el valor este entre 1000 y 4200, puedes hacer el llamado similar a: 

```
peer chaincode query -C mychannel -n asset-transfer -c '{"Args":["ValidateValue","001","1000","4200"]}'
```

## Ejemplo de implementación

Una implementación posible sería:

```
@Transaction(false)
async ValidateValue(ctx: Context, id: string, lower:number, upper:number): Promise<boolean> {
    const existingAssetBytes = await this.#readAsset(ctx, id);
    const existingAsset = newAsset(unmarshal(existingAssetBytes));

    if (existingAsset.AppraisedValue > lower && existingAsset.AppraisedValue < upper){
        return true;
    } else {
        return false;
    }

}
```
