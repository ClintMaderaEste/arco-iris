TestCase0 (Comportamiento del Sistema sin Auto Reparaci�n)
    1 scenario response time - allRepairStrategies, pero no hay ninguna en el stitch
    
	Este test case sirve para mostrar el comportamiento del sistema sin auto-reparacion y sin escenarios.

TestCase1 (Comportamiento con un Escenario, Sin Estrategias)

	Este test case sirve para mostrar el comportamiento del sistema sin auto-reparacion.
	Existe un escenario de response time solamente para poder graficar el threshold, sin embargo, al no existir estrategias, la autoreparacion
	no se disparar�.

TestCase2 (Comportamiento con un Escenario y una Estrategia)

    1 scenario response time - EnlistServerResponseTime

	Un escenario con 1 estrategia, al comienzo el escenario se satisface, luego de unos segundos el escenario se rompe y
	se lanza la adaptacion, escogiendo la estrategia EnlistServersResponseTime.

TestCase3 (Tradeoff entre Estrategias)

    1 scenario response time - prioridad 1 - EnlistServerResponseTime / LowerFidelityReduceResponseTime
    1 scenario cost          - prioridad 2 - ninguna estrategia

	2 escenarios, el escenario de response time tiene 2 estrategias asociadas mientras que el de server cost ninguna, las estrategias
	EnlistServerResponseTime y LowerFidelityReduceResponseTime compiten entre s�, ganando la segunda ya que la primera rompe el 
	escenario de costo.

TestCase4 (Tradeoff entre Escenarios seg�n Prioridades)

    1 scenario response time - prioridad 1 - EnlistServerResponseTime
    1 scenario cost          - prioridad 2 - ReduceOverallCost

	2 escenarios con 1 estrategia cada uno, al comienzo ambos escenarios se satisfacen, luego de unos segundos se rompe el escenario de Response Time,
	este se arregla con la estrategia EnlistServerResponseTime, que agrega 1 servidor, esto hace que se rompa el escenario de Server Cost.
	Luego cambian las condiciones de la simulacion de manera que el response time baja y se hace posible corregir el escenario de server cost mediante
	la estrategia ReduceOverallCost.

TestCase5 (Tradeoff entre Escenarios seg�n Concerns)

    1 scenario response time - prioridad 1 - EnlistServerResponseTime
    1 scenario cost          - prioridad 1 - ninguna estrategia

	2 escenarios con la misma prioridad, uno de costo y otro de tiempo de respuesta, se define por peso del concern 
	asignado en el environment actual (NORMAL)

TestCase6 (Comportamiento Ante Los Cambios en el Entorno de Ejecuci�n)

    1 scenario response time - prioridad 3 - EnlistServerResponseTime - carga normal
    1 scenario response time - prioridad 1 - EnlistServerResponseTime - alta carga
    1 scenario cost          - prioridad 2 - ReduceOverallCost - carga normal

	En carga normal no se repara el tiempo de respuesta porque es mas importante el costo, en cambio en
	alta carga si se repara ya que es prioritario el escenario de mayor prioridad es el de tiempo de respuesta
	de alta carga, y ademas el entorno alta carga otorga mas importancia a la performance.
