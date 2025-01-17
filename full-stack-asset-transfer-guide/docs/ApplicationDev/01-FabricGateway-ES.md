# Fabric Gateway

Desde Fabric v2.4, la [Fabric Gateway client API](https://hyperledger.github.io/fabric-gateway/) es la API recomendada para construir aplicaciones clientes. Existen implementaciones en Go, Node (TypeScript / JavaScript) y Java, cada uno provee identicas capacidades y comportamientos. La API cliente hace uso de un servicio de Fabric Gateway embebido en los peers de Fabric v2.4+. Este topico describe el modelo de Fabric Gateway y las consideraciones para su implementacion en entornos productivos.

Los conceptos que aqui se describen se corresponden directamente a los métodos provistos por el API del cliente, y te ayudaran comprender el comportamiento del API del cliente. Si cuentas con un claro entendimiento del flujo de transacciones en Fabric, puedes considerar este tópico como material de referencia.

## Background

Para comprender como funciona Fabric Gateway, es necesario entender el flujo de las transacciones submit (y evaluate) de Fabric. Este sección provee una breve recapitulación del flujo de transacciones en Fabric desde la perspectiva de un cliente.

### Flujo de transacción de submit

Submit representa una actualización al ledger. En el siguiente diagrama, las lineas sólidas anaranjadas representan interacciones entre el cliente y los nodos de la red, mientras que las líneas entrecortadas verdes representan las interacciones entre los nodos de la red.

![Flujo de transacción submit](../images/ApplicationDev/transaction-submit-flow.png)

1. **Endosar:** el cliente envía la propuesta de la transacción a los peers para su endoso.
    - El peer ejecuta la función de la transacción en el contrato inteligente contra el estado *actual* del ledger para producir un conjunto de lectura/escritura y un valor de retorno para la transacción.
    - Los endosos exitosos deben ser reunidos de una cantidad suficiente de organizaciones para alcanzar los requerimientos de endoso, que puede requerir contemplar las politicas de endoso de chaincode y basada en estado, las invocaciones chaincode-to-chaincode, y las colecciones de datos privados accedidas por la función de la transacción; sino la transacción fallará la *validación* mas adelante.
2. **Submit:** el cliente envía la transacción endosada a un ordenador para que sea incluida en un bloque
3. Los Ordenadores distribuyen los bloques validados a todos los peers de la red, que validan las transacciones contra el estado *actual* de su ledger.  
    - Las transacciones válidas tienen sus conjuntos de lectura/escritura aplicados para actualizar el ledger.
    - Las transacciones invalidas son marcadas con un código apropiado de validación y no actualizan el ledger.
    - Un motivo común para un error de validación es MVCC_READ_CONFLICT, que significa que las llaves del ledger accedidas por la transacción fueron modificadas entre el endoso y la validación. Esto es recuperable ejecutando el flujo de Submit nuevamente.
4. **Commit:** el cliente obtiene el status de confirmación para las transacciones enviadas desde los peers e informa el éxito o fallo de las transacciones enviadas dependiendo del código de validación de la transacción.

### Flujo de Evaluación de las Transacciones 

Evaluate representa una consulta o query y es esencialmente solo el paso de *endosar* del flujo de submit de una transacción.

1. **Evaluate:** el cliente envía una propuesta de transacción a un peer idóneo para endoso y obtiene un valor de retorno.
    - El valor de retorno esta basado en el estado *actual* del ledger del peer que hace el endoso.
    - Las políticas de endoso no tienen que ser satisfechas ya que la transacción no esta siendo enviada para actualizar el ledger.
    - El acceso a las colecciones de data privada debe ser considerado al seleccionar el peer.

## Legacy client SDKs

El siguiente diagrama demuestra como es ejecutada el flujo de envío de una transacción para un cliente que utiliza alguna de las SDK legacy. Las líneas sólidas de color naranja representan transacciones entre el cliente y los nodos de la red, que deben cruzar el firewall en el límite de la red desplegada. Las líneas verdes entrecortadas representan interacciones entre nodos de la red.

Nota que el cliente potencialmente necesita interactuar directamente con alguno o todos los nodos de la red.

![Modelo Legacy SDK](../images/ApplicationDev/legacy-sdk-model.png)

Para que las aplicaciones cliente operen efectivamente, deben hacer uso del servicio de descubrimiento provisto por los peers de la red. Esto requiere interacciones de red adicionales, mas allá de las mostradas en el flujo de envío de transacciones, para:

- Identificar nodos de la red disponibles.
- Obtener un plan de endoso basado en los requerimientos de endoso provistos por el cliente.

## API cliente de Fabric Gateway 

El siguiente diagrama demuestra, para el flujo de envío de transacciones, como es ejecutado para un cliente utilizando el API cliente de Fabric Gateway. Las líneas sólidas naranjas representan intercciones entre el cliente y un peer de tipo Gateway, que debe cruzar el firewall en el límite de la red desplegada. Las líneas verdes entrecortadas representan interacciones entre nodos de la red.

Ten en consideración que el cliente solo necesita interactuar directamente con el peer de tipo Gateway. El peer Gateway opera como un cliente dirigiendo el flujo de envío de transacción dentro de la red desplegada en nombre de la aplicación cliente.

![Modelo con Fabric Gateway](../images/ApplicationDev/fabric-gateway-model.png)

 Como el Gateway es en si mismo un peer, tiene acceso directo a su ledger e información de descubrimiento de servicio. Esto le permite al cliente evitar usar el servicio de descubrimiento y transaccionar utilizando solamente una dirección de punto de entrada del Gateway. El peer Gateway generalmente puede determinar automáticamente un plan de endoso apropiado, evitando que el cliente necesite conocer los requisitos de endoso.

 Como referencia, se puede encontrar una descripción más detallada del servicio Fabric Gateway y su comportamiento en la [documentación de Fabric](https://hyperledger-fabric.readthedocs.io/en/release-2.4/gateway.html).

## Despliegue en Producción de Fabric Gateway

Por seguridad, las aplicaciones cliente deberían conectarse solamente a peers Gateway dentro de su propia organización o, si la organización cliente no aloja sus propios peers, a peers Gateway de una organización de confianza.

El siguiente diagrama demuestra la práctica recomendada para habilitar el acceso a un clúster de una organización a través de una única dirección de punto de entrada, manteniendo alta disponibilidad. Este uso de un balanceador de carga o controlador de ingreso como proxy frente a un conjunto de puntos de entrada internos es usado comúnmente al desplegar servidores Web o de Aplicaciones, por lo que este patrón está bien establecido. La comunicación gRPC entre el cliente y el Gateway en realidad utiliza HTTP/2 como su transporte.

![Despliegue de Fabric Gateway](../images/ApplicationDev/fabric-gateway-deployment.png)

Un enfoque alternativo (o complementario) que se puede emplear es asignar múltiples registros a un único nombre DNS de Gateway. Esto permite que los clientes seleccionen de un conjunto de direcciones IP de peers Gateway asociadas con un único punto de entrada del Gateway.

Es importante tener en cuenta que los peers deben incluir la dirección del punto de entrada visible externamente en sus certificados TLS para que los clientes puedan completar con éxito el handshake TLS.

Como referencia, se puede encontrar más información en la documentación de gRPC:

- [Balanceo de carga gRPC](https://grpc.io/blog/grpc-load-balancing/).
- [Resolución de nombres gRPC](https://grpc.github.io/grpc/core/md_doc_naming.html).
