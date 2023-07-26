# Desarrollo de Contratos Inteligentes

En esta sección introduciremos el concepto de Contrato Inteligente, y como la plataforma de Hyperledger Fabric maneja estos contratos. Hablaremos sobre algunos de los aspectos importantes a tener en cuenta; estos pueden ser distintos de los que aplica para otro tipo de desarrollo.

Este ejemplo muestra como los detalles de un activo pueden ser almacenados en el ledger mismo, con el Contrato Inteligente controlando el ciclo de vida del activo. Provee funciones transaccionales para:

- Crear un activo
- Recuperar (uno o todos) los activos
- Actualizar un activo
- Borrar un activo
- Transferir propiedad del activo entre partes.

Este tópico aborda consideraciones de diseño comunes para los contratos inteligentes. Si quieres ir directamente al código del workshop, puedes ir directamente a [comenzar](./01-Exercise-Getting-Started-ES.md) y regresar a esta página posteriormente como referencia.

Por favor recuerda que Hyperledger Fabric es un Blockchain Ledger y no una Base de Datos!

[SIG - Comenzar con un Contrato Inteligente](./01-Exercise-Getting-Started-ES.md)

---
## Diseñar los activos y contratos

La primera y quizás la decision mas importante es - "¿qué información necesita estar bajo el control del
 ledger?".  Esto es importante por varias razones.

- El ledger es el estado compartido entre organizaciones, ¿aplica que todas las organizaciones puedan ver la información que esta siendo compartida? Ten en cuenta que las colecciones privadas de data de Fabric pueden ser usadas cuando un subconjunto de la data del ledger tiene que permanecer privada entre partes.
- Para cualquier actualización en el estado del ledger, ¿qué organizaciones deben ejecutar el contrato inteligente y 'endosar' los resultados antes para que el cambio sea considerado una transacción válida? ¿La política de endoso será fija, o basada en la data? Por ejemplo, puedes desear que la organización del propietario actual la endose, junto con la organización del regulador. Esto puede ser logrado con las capacidades de endoso basadas en estado.
- ¿Cuán grande es el tamaño de la data? Es mejor cuanto mas chica sea. Recuerda que esta data tiene que ser transferida entre muchas partes y la historia de las transacciones es mantenida en el ledger. ¿Funcionaría mejor un hash seguro salado de un documento escaneado a guardar el documento completo? 
- Aunque es posible realizar consultas del estado con JSON enriquecido cuando se usa CouchDB como base de datos del estado, el peer y el ledger están optimizados para procesamiento transaccional mas que consultas. ¿Pueden las consultas ser realizadas fuera del ledger, y los resultados 'validados' mediante un contrato inteligente? 

Para cualquier tutorial de punta-a-punta existe una contrapartida entre hacer el escenario realístico, pero no suficientemente complicado. Para este tutorial definiremos la data a ser almacenada en el ledger como un activo de un único 'objeto' con los siguientes campos. Esto se ha mantenido bastante simple, pero el enfoque debería ser familiar.

- ID: cadena de caracteres identificador unívoco
- Color: cadena de caracteres representando un color
- Size: valor numérico representando un tamaño
- Owner: cadena de caracteres representando la identidad del propietario del activo (id de la organización mas el nombre común (CN) del certificado del cliente)
- Appraised Value: valor numérico

Probablemente no todos estos valores requieran estar en el ledger, la data podría estar almacenada en un 'oráculo' fuera del ledger que proveerá un hash de la data a ser guardada en el ledger de blockchain.

### Claves y Consultas

Piensa en la base de datos de estados del ledger como un repositorio de clave-valor para persistir los activos, o más genéricamente, cualquier registro de data que quieras mantener en el ledger.

Es importante tener cuidado al elegir la 'clave' que será usada. Podrías usar claves simples tales como una clave lógica pasada desde un sistema externo, una UUID, o incluso usar la txid de la transacción que creo el activo. Las claves compuestas son también posibles. Éstas son construidas para formar una estructura jerárquica y permitir otros tipos de consultas basadas en claves.

Una cadena de caracteres para la clave puede ser armada desde una lista de cadena de caracteres separados por el byte nulo `u+0000`. Debe haber al menos una cadena en la lista. Si solo hay una cadena, esto es referido como una clave 'simple', caso contrario, es una clave 'compuesta'. Por ejemplo puedes desear crear una clave compuesta basada en el 'tipo' de activo y el ID. Las APIs de contratos inteligentes te ayudan a crear fácilmente estas claves compuestas.

Para claves simples, puedes hacer consultas con la misma utilizando la API `getState(key: string)`.

Puedes consultar también por un rango de claves usando la API `getStateByRange(startKey: string, endKey: string)`. Las consultas por rangos te permiten especificar una clave de comienzo y una de fin, y regresa un iterador de todos los clave-valor que se encontraban entre esos puntos de comienzo y fin (incluidos estos). Las claves estarán ordenadas alfanuméricamente.

Las claves compuestas proveen un mecanismo de consulta interesante ya que ofrecen una consulta de rango por clave parcial. Por ejemplo, si una clave compuesta tiene las cadenas `fruit:pineapples:supplier_fred:consignment_xx` (usamos aquí un colon para que sea fácil de leer ya que el byte nulo no lo es) entonces es posible emitir consultas con una clave puntera parcial.
Por ejemplo, para consultar por todos los registros de ananás que tiene `supplier_fred` podrías hacer la consulta utilizando la clave parcial `fruit:pineapples:supplier_fred`.

Una manera de ver esto es visualizando las claves como si formasen una jerarquía.

Ten en cuenta que las claves 'simple' y 'compuesta' son almacenadas de manera distinta entre ellas. Consecuentemente una consulta por una clave simple no regresará nada que esté guardado bajo una clave compuesta, a la inversa, una consulta por una clave compuesta no regresa nada que esté guardado bajo una clave simple. 

Los tipos de consultas mencionadas anteriormente están soportadas en ambas bases de datos de estado, tanto LevelDB como CouchDB, y consultan las 'claves' del repositorio clave-valor. 


Si utilizas CouchDB, puedes también consultar por el 'valor' del par clave-valor utilizando una consulta JSON enriquecida. Esto requiere que el valor se encuentre en formato JSON (como en este tutorial). Los indices en CouchDB pueden ser provistos en el paquete del contrato inteligente para hacer que las consultas JSON sean eficientes (y esto es altamente recomendado). Aún así ten presente que las consultas basadas en 'valores' nunca serán tan eficientes como las basadas en 'clave'.  

## Funciones Transaccionales

Veamos cuales son los diferentes tipos de funciones transaccionales que pueden ser escritos en el Contrato Inteligente. Cada uno de estos puede ser invocado desde la aplicación cliente.
- Funciones de tipo 'Evaluate' son invocadas de manera solo-lectura para consultar el estado en la base de datos del ledger en un peer específico.
- Funciones de tipo 'Submit' son invocadas para enviar una transacción a todos los peers a los que se require que endosen los cambios al ledger, resultando en una operación de escritura o una operación de lectura-escritura que es enviada al servicio de ordenamiento y finalmente ejecutada en todos los peers.

### Aspectos Generales 

Cada función transaccional del contrato inteligente tiene que ser marcada como tal (utilizando convenciones específicas al lenguaje). Puedes también especificar si la función esta pensada para ser 'enviada' o 'evaluada'. Esto no es obligatorio pero es una indicación para el usuario.

Cada función deberá considerar cómo manipulará la data para ordenarla al formato requerido para el ledger.

Cada función debe asegurarse que cada estado inicial es el correcto. Por ejemplo, antes de transferir un activo de Alice a Bob, debe asegurar que Alice es la propietaria, y que Alice es la identidad que esta enviando la transacción de transferencia.

### Funciones de Creación

Considera en la función de creación si quieres pasar individualmente cada elemento de data o un objeto completamente formado. Esto es mas una cuestión de preferencia personal, sin embargo, recuerda que cualquier identificador unívoco debe ser creado por fuera del contrato inteligente. La transacción será ejecutada en múltiples peers y los resultados deben coincidir por lo que no pueden ser usados cualquier función de elección aleatoria u otros procesos no determinísticos.

Con frecuencia hay elementos extras de data (tales como la organización que envía) que deben ser agregados.

### Recuperar

Es una buena idea pensar de antemano en los tipos de operaciones de recuperación o consulta que harán falta. ¿Puede la estructura de la clave ser creada para permitir consultas por rangos?

Si son requeridas consultas con JSON enriquecido, procura hacerlas lo más simple posible e incluye índices. Asegúrate además que si deseas hacer una consulta con JSON enriquecido que involucra la misma data como la 'clave' que este incluído en la estructura JSON como parte del 'valor'.

Existe un ejemplo de consultas de tipo obtener-todo en este workshop. Por favor considera que con el paso del tiempo esto podría recibir una gran cantidad de data que conlleva un costo en performance, ¡por lo cual generalmente no es recomendado! 

Para consultas avanzadas, considera crear un data store aguas abajo optimizado para el tipo de consultas que necesitas. El [ejemplo de data off-chain](https://github.com/hyperledger/fabric-samples/tree/main/off_chain_data) demuestra como construir un data store aguas abajo basado en los eventos de bloques.

### Leer-tus-propias-escrituras y conflictos

Las actualizaciones que una función transaccional hace al estado, no son realizadas inmediatamente; conforman un juego de cambios que deben ser endosados y ordenados. Este comportamiento asíncrono genera 2 consecuencias importantes.

Si la data bajo una clave es actualizada, y luego consultada *en la misma función del contrato inteligente* la data regresada sera el valor *original* - y no el valor actualizado.

Adicionalmente, puedes llegar a observar transacciones invalidadas con un error 'MVCC Conflict': esto significa que dos funciones transaccionales se ejecutaron al mismo tiempo y trataron de leer y actualizar las mismas claves. La primera transacción a ser ordenada en un bloque será validada, mientras que la segunda transacción será invalidada ya que la entrada de lectura ha cambiado desde la ejecución del contrato. Diseña tus claves y aplicaciones para que las mismas claves no sean actualizadas concurrentemente. Si esta es una ocurrencia inusual simplemente compénsala en la aplicación, por ejemplo, enviando nuevamente la transacción.

## Registro de Auditoría vs Registro de Activos

Una decisión importante a realizar es si el estado que se guarda en el ledger representa un 'registro de auditoría' de las actividades o la 'fuente de la verdad' del activo mismo. Como se observará en los ejemplos siguientes, almacenar la información de los activos es conceptualmente sencillo pero se debe tener en cuenta que esta es una base de datos distribuida mas que solo una base de datos.

Almacenar un tipo de registro de auditoría puede funcionar bien con el concepto de ledger. La 'fuente de la verdad' aquí es que cierta acción fue tomada y su resultado. Por ejemplo la propiedad de un activo cambió. Los detalles del activo en sí pueden ser almacenados fuera del ledger (off-chain). Esto si significa que se debe considerar mas infraestructura alrededor del ledger pero vale la pena contemplarlo si la principal justificación de negocio es el registro de auditoría. Por ejemplo, seguir el estado de un proceso y como se movió de un estado al siguiente.

Para ayudar con la integración de otros sistemas bien vale la pena emitir eventos desde la función transaccional. Estos eventos estarán disponibles para las aplicaciones cliente cuando la transacción este finalmente realizada. Estos pueden ser muy útiles para disparar otros procesos.

## ¿Es un Contrato Inteligente o un Chaincode?

Simplemente ambos - los términos han sido utilizados en la historia de Fabric casi que como equivalentes, Chaincode fue el nombre original, pero Contrato Inteligente es un término común en blockchain. La clase/estructura que ha sido extendida/implementada en el código se llama `Contract`.

El objetivo es estandarizar en:
- los Contratos Inteligentes son las clases/estructuras - el código - que escribes en Go/JavaScript/TypeScript/Java etc.
- éstos son luego empaquetadas y se ejecutan dentro de un contenedor de Chaincode (imagen de chaincode / ambiente de ejecución de chaincode dependiendo exactamente en el formato del paquete)
- la definición del chaincode es más que tan solo el código del Contrato Inteligente, ya que incluye tales cosas como los índices de CouchDB, y la política de endoso.

## Empaquetado

En la versión 1.x de Hyperledger Fabric, y aún soportado como 'el ciclo de vida antiguo' en v2.x, el formato de paquete de chaincode CDS fue utilizado. El 'nuevo ciclo de vida' de v2.x debe ser utilizado de ahora en más - con el formato estándar `tar.gz`. Usar `tar` y `gzip` son técnicas estándar para herramientas estándar. Con lo cual la cuestión principal pasa a ser qué es lo que irá dentro de esos archivos y cuándo / cómo son usados.
