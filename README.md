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

