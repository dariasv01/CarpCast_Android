# CarpCast

CarpCast es una aplicación Android que calcula un índice de actividad de pesca (por ejemplo, para carpa u otras especies) combinando datos meteorológicos, astronómicos e hidrológicos. La aplicación consume APIs públicas (Open-Meteo, servicios de astronomía y APIs de hidrología) y ejecuta localmente el motor de scoring que devuelve pronósticos por hora, ventanas óptimas y explicaciones.

Este README resume cómo construir, ejecutar y entender el proyecto, y dónde mirar para extender o depurar el motor de predicción.

---

## Contenido rápido

- URL del repositorio: (local en tu máquina)
- Plataforma: Android (Kotlin)
- Build: Gradle (incluido `gradlew`)
- Lenguajes principales: Kotlin

---

## Requisitos

- Android Studio (recomendado) o JDK + Gradle
- JDK 11+ (o el requerido por tu configuración Gradle)
- Android SDK con las API targets del proyecto
- Conexión a Internet para consumir APIs externas (Open-Meteo, astronomía, hidrología)

---

## Estructura del proyecto (resumen)

- `app/` - módulo Android principal
  - `src/main/java/com/david/carpcast/repository/ForecastRepository.kt` — composición de las llamadas a APIs, construcción de `WeatherData` horario, cálculo de `estimatedWaterTemps`, invocación a `ActivityScoring`, generación de `bestWindows` y empaquetado JSON.
  - `src/main/java/com/david/carpcast/scoring/ActivityScoring.kt` — motor de scoring (equivalente a scoring.ts). Contiene: `estimateWaterTempSeries`, `computeDerivedFeatures`, `calculate`, `findBestWindowsDetails`, configuración por especie (`getSpeciesConfig`) y funciones auxiliares.
  - `src/main/java/com/david/carpcast/astronomy/` — clientes y factory para datos astronómicos (USNO -> fallbacks Sunrise-Sunset.org / FarmSense).
  - `src/main/java/com/david/carpcast/hydrology/` — cliente de hidrología y parsing de muestras.
  - `res/values*/strings.xml` — traducciones y cadenas.
  - `AndroidManifest.xml`, recursos y layouts.

---

## Flujo de la predicción (alto nivel)

1. Obtener datos crudos:
   - Open-Meteo: pronóstico horario (`hourly`), `daily` y `current_weather`.
   - AstronomyClient: sunrise/sunset, moon phase/illumination (intenta USNO primero, luego fallbacks).
   - HydrologyClient: muestras (nivel, caudal, temperatura del agua) si están disponibles.
2. Construir lista `weatherHours` (tipo `WeatherData`) con todos los campos horarios (temp, presión, humedad, viento, precip, pop, cloud covers, SW radiation, uvi, is_day, dewPoint, etc.).
3. Calcular `estimatedWaterTemps` con `ActivityScoring.estimateWaterTempSeries(...)` usando el seed de hidrología si existe.
4. Para cada hora:
   - Calcular features derivados con `computeDerivedFeatures` (deltas de presión, lluvia acumulada, estabilidad de viento, etc.).
   - Determinar `waterTempForScoring`: si la hora está a ±2 h del `anchorNow` y existe `hydro.waterTemp` se usa la medida; si no, se usa la estimación.
   - Construir `ActivityCalculateArgs` y llamar `ActivityScoring.calculate(...)`.
5. Guardar resultados por hora y calcular `bestWindows` (usando `ActivityScoring.findBestWindows`) y `bestWindowsToday` filtradas por fecha.
6. Devolver JSON con `forecasts`, `bestWindows`, `bestWindowsToday`, `debug_scoring_dump` y `dataAvailability`.

---

## Puntos clave del motor de scoring

- Arquitectura W/C: el scoring meteórico está dividido en dos vectores:
  - W (estado actual) — variables inmediatas (viento, nubosidad, precipitación, temperatura, presión, radiación, amanecer/crepúsculo, etc.).
  - C (cambios / acumulados) — variables dependientes de contexto (delta presión 1h, promedio 3h, lluvia prev 6h, lluvia 24h, estabilidad del viento).
- Cada especie tiene un `SpeciesConfig` con pesos W, pesos C y funciones de normalización para cada feature.
- Se calcula `wNorm` y `cNorm` con `weightedNorm01` y se combinan con un mix W/C que depende del horizonte (h ≤ 6h => más peso W; a mayor horizonte más peso a C).
- El score final combina meteo + astro + hydro + marine con pesos base (40/30/20/10 renormalizados) y después aplica multiplicadores biológicos (seasonFactor, spawnPenalty, nightFactor, moonFactor).
- La confianza se calcula como: base 0.65 + 0.1 si hay hydro + 0.1 si marine + 0.1 si derived tiene info relevante + 0.05 si hay measurement de waterTemp.

---

## Comandos habituales (Windows PowerShell)

Abrir PowerShell en la raíz del proyecto y ejecutar:

- Compilar (debug):

```powershell
.\gradlew.bat assembleDebug
```

- Ejecutar tests (si hay tests configurados):

```powershell
.\gradlew.bat test
```

- Limpiar:

```powershell
.\gradlew.bat clean
```

Recomendado: usar Android Studio para ejecutar y depurar en emulador o dispositivo físico.

---

## Cómo depurar/validar el scoring localmente

- El repositorio ya construye `debug_scoring_dump` por cada hora (JSON con `derived`, `breakdown`, `weights`, `wNorm`, `cNorm`, `waterTempForScoring`, `activity_overall`, `activity_confidence`).
- Para comparar servidor vs cliente:
  1. Ejecuta la ruta que genera el JSON (o guarda el JSON crudo de Open-Meteo + astro + hydro).
  2. En tu ejecución local, asegúrate de usar la misma entrada exacta (mismas horas y valores) y el mismo `anchorNowMs` si quieres reproducir resultados idénticos.
  3. Compara `debug_scoring_dump` campo a campo (`wNorm`, `cNorm`, `mix`, `waterTempForScoring`) y verifica divergencias.

- Problema común: `DateTimeParseException` si la fecha/hora no tiene offset (ej. "2026-02-02T07:00") y se intenta parsear con `OffsetDateTime.parse`. El repositorio incluye una función robusta `parseTimeMs` en `ForecastRepository.kt` que intenta varios formatos; unifica su uso si tienes errores de parsing.

---

## Personalización rápida (diferenciar especies)

- Archivo a editar: `app/src/main/java/.../ActivityScoring.kt` → función `getSpeciesConfig(species)`.
- Puedes ajustar:
  - Pesos W y C (mapas `W` y `C`).
  - Funciones de normalización `f` para cada feature (por ejemplo `temp`, `ptrend3h`, `radiation`).
  - Mix W/C en `meteoMixForHorizon` para controlar cuánto importa estado actual vs acumulados según el horizonte.

Nota: antes de tocar pesos, añade logs (`debug_scoring_dump`) y tests para validar efectos.

---

## Internacionalización

- Las cadenas están en `app/src/main/res/values*/strings.xml` (es, en, fr, de, pt-rBR...). Para añadir idiomas, crea `values-xx/strings.xml` con las claves necesarias.

---

## Problemas conocidos y soluciones

- Date parsing: usar la función robusta `parseTimeMs` de `ForecastRepository` (intenta varios formatos y asume UTC cuando falta zona).
- `j$.time.Instant vs java.time.Instant` error: revisar imports y configuración de desugaring de java.time en Gradle si aparecen incompatibilidades en ciertos niveles de API.
- Best windows: `ActivityScoring.findBestWindows` asume que la lista está ordenada cronológicamente y obliga a evitar solapamientos por índices; si los resultados parecen raros, comprueba que la lista de tiempos esté correctamente ordenada.

---

## Contribuir

1. Abre un issue describiendo el cambio o bug.
2. Crea una rama: `feature/my-change`.
3. Añade pruebas unitarias para cambios en scoring y ejecuta `./gradlew.bat test`.
4. Haz PR y documenta los cambios en este README si afectan la lógica del scoring.

---

## Contacto / referencia rápida

- Scoring core: `app/src/main/java/com/david/carpcast/scoring/ActivityScoring.kt`
- Orquestación y construcción de JSON: `app/src/main/java/com/david/carpcast/repository/ForecastRepository.kt`
- Clients externos: `app/src/main/java/com/david/carpcast/astronomy` y `app/src/main/java/com/david/carpcast/hydrology`

---

Si quieres, puedo:
- Añadir una sección en inglés y otras traducciones del README.
- Generar tests unitarios para `estimateWaterTempSeries`, `computeDerivedFeatures` y `findBestWindowsDetails`.
- Unificar `parseTimeMs` y reemplazar usos inseguros para evitar errores de parse.

EOF



## Revisión del modelo de carpa (noviembre de 2026)

El índice es orientativo: no expresa probabilidad de captura. El podcast de Spanish
Carpers es una referencia cualitativa sobre la carpa, no un conjunto de datos de entrenamiento.

- `ActivityScoring` es el único motor; `ScoringTsPort` conserva la API anterior como adaptador.
- Se solicitan datos actuales, amanecer/atardecer diarios y presión a nivel del mar
  (`pressure_msl`). Esta última permite aplicar los umbrales existentes sin confundir
  altitud con bajas presiones. La zona del destino y el offset de cada fecha se conservan
  en las horas del JSON y en la pantalla.
- Cada hora utiliza su propio amanecer/atardecer. `is_day` se interpreta como 0/1.
  Actualizar realiza una nueva petición y conserva el pronóstico anterior si falla.
- Para carpa, las curvas térmicas se interpolan; los cambios térmicos utilizan la serie
  estimada del agua, no el cambio de temperatura del aire. Una medida aislada no aporta
  una tendencia medida. La estimación continúa siendo una aproximación superficial sin
  profundidad, estratificación ni oxígeno disuelto.
- Las antiguas bonificaciones por lluvia pasan a depender del contexto térmico:
  por debajo de 12 °C se invierte la bonificación, por encima de 18 °C se conserva,
  y entre ambos valores se interpola; sin lluvia o contexto térmico son neutrales.
  Estos puntos son hipótesis provisionales, NO umbrales calibrados por el podcast.
- Solo para carpa se desactivan los multiplicadores de luna, estación y reproducción,
  el bloque astronómico independiente y la puntuación hidrológica por mera disponibilidad.
  El amanecer y atardecer conservan su peso meteorológico previo. Los pesos relativos
  de los factores meteorológicos existentes no se han vuelto a ajustar.
- `confidence` se conserva por compatibilidad, pero ahora representa cobertura de datos,
  también publicada como `dataCoverage`. Se cuentan diez entradas: temperatura del aire,
  presión, humedad, viento, precipitación, nubosidad, día/noche, tendencia de presión,
  temperatura del agua disponible y medida de agua verificada. Los valores de relleno
  no cuentan como datos presentes. Una estimación no cuenta como medida.
- La procedencia del agua aparece en cada hora (`measured`/`estimated`) y en pantalla.
  Solo se aceptan como medidas muestras de temperatura en Celsius, con offset y
  antigüedad/proximidad de hasta dos horas. La serie estimada se inicia con el aire;
  una observación actual no se aplica como si hubiera ocurrido a medianoche.
- Se retira la tarjeta inicial que mostraba puntuación y horario fijos sin historial real.

### Calibración pendiente

Se inspeccionó el archivo local `jornadas_pesca.json`: 11 jornadas y 31 capturas;
ninguna jornada sin capturas, sin campos explícitos de inicio/fin o esfuerzo de pesca.
No se han recalibrado pesos con esa muestra: faltan resultados negativos, esfuerzo,
condiciones históricas y separación de jornadas para entrenamiento y evaluación.
Para recopilarlos, registrar inicio/fin, número de cañas, lugar, especie objetivo,
capturas (incluidas cero), hora de cada captura y temperatura medida con profundidad.
Comparar por jornadas completas y por capturas por hora de caña, conservando datos
independientes para evaluación. Los registros personales no se añaden al repositorio.

Referencia del contrato de la API: https://open-meteo.com/en/docs

### Validación

`gradlew.bat :app:testDebugUnitTest :app:assembleDebug` compila y ejecuta las pruebas.
Java y Kotlin generan bytecode para JVM 21 de forma explícita. Las pruebas de regresión
cubren continuidad térmica, lluvia contextual, enfriamiento, ausencia de bonificaciones
hidrológicas/lunares/estacionales para carpa, procedencia del agua y zonas horarias.
Las pruebas verifican el comportamiento del código; no miden precisión de captura.


### Selección de especie y modo

`SpeciesSelection` es la fuente compartida para interpretar el identificador y determinar
el modo: carpa/barbo usan `carpfishing`; black bass/lucio/siluro usan `predator`.
El repositorio publica ambos campos y la pantalla deriva el modo de la especie también
para JSON antiguos que omitían el modo o lo fijaban erróneamente a `carpfishing`.
Esto activa las configuraciones específicas existentes; no recalibra sus pesos ni
valida su precisión biológica. Las pruebas recorren las cinco especies y las dos
entradas al motor para evitar la caída accidental al modelo general.


## V2: diario conectado y revisión visual

- Diario privado con inicio/fin, cañas, escenario, profundidad, temperatura medida,
  notas y capturas con especie, fecha, hora y peso. Las jornadas finalizadas con cero
  capturas se conservan. El esfuerzo usa cañas simultáneas constantes por duración
  total; todavía no distingue pausas ni cambios de cañas a mitad de jornada.
- Iniciar desde el pronóstico guarda una copia inmutable del JSON original. El
  resultado muestra el índice horario previsto durante la jornada; actualizar el
  pronóstico no cambia esa copia. Los archivos del diario se escriben mediante
  AtomicFile y se verifica su contenido después de guardar.
- Último pronóstico real disponible sin conexión, fecha de actualización visible y
  aviso de copia guardada. Los resúmenes se agrupan por el día mostrado, también al
  abrir archivos de días anteriores. El acceso al diario está en el inicio.
- Las recomendaciones solo contienen horas futuras completas y consecutivas. Las
  ventanas no se solapan y su fin corresponde al final de la última hora incluida.
- Las tres explicaciones destacadas proceden de contribuciones reales del modelo
  meteorológico respecto a una entrada neutral. No describen probabilidad de captura.
  El cálculo ya no comparte estado mutable entre peticiones para estas explicaciones.
- Exportación manual del diario en JSON desde el selector de archivos de Android.
  No se recalibran pesos automáticamente con los nuevos registros.
- Tema oscuro azul coherente, tarjetas sin sombras pesadas, selector de especies
  compacto, resumen sin filas saturadas, formulario de captura en pantalla completa,
  etiquetas en español y gráfica con eje horario y escala fija 0–100. Se retiran los
  controles de depuración de carga y la interfaz deja de modificar ese estado.

### Comprobaciones de la V2

Pruebas JVM para esfuerzo, bolos, validación de registros, ventanas y contribuciones.
Robolectric 4.17 renderiza las pantallas Android con gráficos nativos y prueba el flujo
de iniciar una jornada, añadir captura y finalizar. Se revisa también el pronóstico
con texto al 130 %. Las imágenes generadas están en `app/build/qa` y usan datos de prueba;
la vista del mapa de inicio es un placeholder de previsualización.

En Windows se usa un shadow de AtomicFile que reproduce la semántica de reemplazo
de rename de Android; java.io.File.renameTo en Windows no reemplaza el destino.
Las pruebas instrumentadas compilan y usan AtomicFile real, pero requieren dispositivo
o emulador disponible para ejecutarse. Los ensayos locales no sustituyen la revisión
del mapa, permisos, teclado, exportación por el selector del sistema y bordes de pantalla
en un dispositivo real. El directorio temporal y SDK de Robolectric quedan dentro de
`app/build`. Comando: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`.
