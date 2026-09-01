# Checklist de pruebas — ERP Cloud

Orden sugerido: de abajo hacia arriba en dependencias (Auth → catálogos base → módulos que dependen de ellos).

\---

## 1\. Autenticación y sesión (`Login` / `AuthContext` / `tenantSession` / `api.js`)

* \[✅ ] Login con credenciales válidas → redirige al Dashboard.
* \[✅ ] Login con credenciales inválidas → muestra mensaje de error, no redirige.
* \[✅ ] Después de login exitoso, `localStorage.getItem('token')` tiene el JWT real.
* \[✅ ] `localStorage.getItem('companyId')` tiene el `companyId` real devuelto por el backend (no "4" fijo).
* \[✅] Header `X-Tenant-Id` viaja correctamente en las peticiones (verificar en Network → Request Headers).
* \[✅ ] Header `Authorization: Bearer ...` viaja correctamente.
* \[✅] Recargar la página (F5) estando logueado → sigue autenticado, no pide login de nuevo.
* \[✅] Cerrar sesión (`Cerrar sesión`) → limpia `localStorage`, redirige a Login.
* \[✅ ] Después de logout, intentar navegar de vuelta al Dashboard (URL o botón atrás) → no debe mostrar datos.
* \[✅ ] Cambiar idioma (ES/EN) en Login y en Dashboard → toda la UI cambia de idioma correctamente.
* \[ ] `AppHeader` muestra el `companyId`/nombre de compañía correcto en cada módulo.

⚠️ \*\*Bug conocido, pendiente de arreglar\*\*: justo después del login se

> muestra el `companyId` crudo (ej. "4") porque el backend no devuelve
  > un nombre de compañía todavía (`companyName: null` en `Login.jsx`).
  > Después de recargar la página (F5), `tenantSession.js` pierde su
  > estado en memoria y vuelve al valor de fábrica hardcodeado
  > ("Compañía Demo S.A."), que \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*no\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\* es el nombre real — es solo el
  > placeholder con el que armamos `tenantSession.js` al principio.
  > Las peticiones siguen autenticadas correctamente (el interceptor usa
  > el token real de `localStorage`); el problema es puramente visual.
  > \\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*Causa raíz\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\\*: falta implementar la hidratación real de
  > `tenantSession.js` al arrancar la app (leer `token`/`companyId` de
  > `localStorage`, o idealmente una llamada `/me` al backend), en vez
  > de depender del estado inicial hardcodeado. Ya estaba anotado como
  > `TODO` en `AuthContext.jsx` desde que se construyó — pendiente de
  > implementar.

* 

\---

## 2\. Terceros (`ThirdPartyPage`)

**Listado y navegación**

* \[✅] Carga la tabla de terceros sin errores en consola.
* \[✅ ] Buscador filtra por identificación, nombre, razón social, ciudad.
* \[✅ ] Paginación (Anterior/Siguiente) funciona y respeta el límite del backend.

**Crear — Persona Natural**

* \[✅] Crear tercero Natural con datos válidos → éxito, aparece en la tabla.
* \[✅] Dejar Primer Nombre o Primer Apellido vacíos → error de validación en frontend, no se envía al backend.
* \[✅] Régimen fiscal: al elegir "Natural", el `<select>` solo muestra los 3 regímenes válidos para Natural (`INDIVIDUAL`, `VAT\\\\\\\\\\\\\\\_NOT\\\\\\\\\\\\\\\_REGISTERED`, `VAT\\\\\\\\\\\\\\\_REGISTERED`).
* \[✅ ] Cambiar de Natural → Jurídica y volver a Natural → el régimen se resetea/ajusta correctamente, sin quedar un valor inválido.

⚠️

**Crear — Persona Jurídica**

* \[✅] Crear tercero Jurídico con datos válidos → éxito.
* \[✅] Dejar Razón Social vacía → error de validación.
* \[✅] Régimen fiscal solo muestra las 4 opciones válidas para Jurídica (`CORPORATE`, `VAT\\\\\\\\\\\\\\\_REGISTERED`, `SPECIAL\\\\\\\\\\\\\\\_REGIME`, `GRAND\\\\\\\\\\\\\\\_TAXPAYER`).

⚠️



**Validación de pestañas**

* \[✅] Guardar con un campo faltante en la pestaña "Ubicación" estando parado en "Datos Básicos" → salta automáticamente a la pestaña con el error, y la marca con punto rojo.
* \[ ✅] Mismo caso con un campo faltante en "Clasificación Fiscal".

**Centro de costo por defecto**

* \[✅ ] El `<select>` de centro de costo **solo** muestra centros con "Permite Movimiento" = sí (no debe listar centros padre/header).

**Panel "Ver" (solo lectura)**

* \[ ] Abrir "Ver" en un tercero → muestra los datos correctos en las 3 pestañas, sin inputs editables.
* \[ ] Régimen fiscal se muestra con el nombre correcto, incluyendo el desambiguado `(Natural)`/`(Jurídica)` para `INDIVIDUAL`/`CORPORATE`.
* \[ ] Botón "Editar" dentro del panel "Ver" abre el formulario de edición con los datos precargados.

**Editar**

* \[ ] Editar un tercero existente → los campos cargan correctamente, incluida ciudad y centro de costo.
* \[ ] Cambiar el número de documento de un tercero **con movimientos contables** → el backend lo permite (comportamiento intencional, confirmado).

**Activar / Desactivar**

* \[ ] Desactivar un tercero **sin** movimientos contables → éxito.
* \[ ] Desactivar un tercero **con** movimientos contables → error traducido: *"No se puede desactivar este tercero porque tiene movimientos contables..."*
* \[ ] Activar un tercero inactivo → vuelve a aparecer como activo, botón cambia a "Desactivar".

**Errores del backend**

* \[ ] Crear tercero con documento duplicado → mensaje traducido de "ya existe un registro con este valor", no un mensaje genérico o en inglés fijo.

\---

## 3\. Centros de Costo (`CostCenterPage`)

* \[ ] Crear centro de costo raíz (sin padre) → éxito.
* \[ ] Crear centro de costo hijo (con padre) → éxito, nivel se calcula correctamente.
* \[ ] Al editar un centro **con hijos**: el toggle "Permite Movimiento" aparece deshabilitado, con nota explicativa.
* \[ ] Intentar (vía backend/Postman si es necesario) forzar `allowsMovement=true` en un centro con hijos → rechazado con mensaje traducido.
* \[ ] Selector de "Centro Padre" **no** permite elegir el propio centro ni ninguno de sus descendientes (evita ciclos).
* \[ ] Elegir como padre un centro que ya tiene "Permite Movimiento" = sí → aparece advertencia ámbar (no bloqueante).
* \[ ] Desactivar un centro → éxito, botón cambia a "Activar".
* \[ ] Activar un centro inactivo → vuelve a estar activo.
* \[ ] Tenant (`AppHeader`) se actualiza reactivamente, no requiere recargar página.

\---

## 4\. Impuestos (`TaxPage`)

* \[ ] Crear impuesto con tarifa entera (ej. `19`) → guarda correctamente.
* \[ ] Crear impuesto con tarifa **decimal** (ej. `0.966` para ICA) → el punto decimal **no desaparece** mientras se escribe.
* \[ ] Base mínima también acepta decimales sin el mismo bug.
* \[ ] Selector de cuenta contable: intentar guardar sin seleccionar cuenta → error de validación.
* \[ ] Elegir una cuenta contable que **no** es de movimiento (`postingAccount=false`) → error traducido: *"La cuenta contable seleccionada no es una cuenta de movimiento."*
* \[ ] Elegir una cuenta contable **inactiva** → error traducido: *"La cuenta contable seleccionada está inactiva."*
* \[ ] Desactivar / Activar impuesto → funciona correctamente en ambos sentidos.
* \[ ] **Crítico**: crear un impuesto nuevo y confirmar que **no** falla con `Column 'company\\\\\\\\\\\\\\\_id' cannot be null` (bug crítico corregido — requiere que ya hayas aplicado el fix de `CompanyRepository` en `TaxService`).

\---

## 5\. Tipos de Documento (`DocumentTypePage`)

* \[ ] Crear tipo de documento nuevo, definiendo el consecutivo inicial → éxito.
* \[ ] Editar un tipo de documento existente → el campo "Consecutivo" aparece **bloqueado** (solo lectura), con nota explicando que se ajusta con la acción dedicada.
* \[ ] Cambiar código, nombre, prefijo, resolución legal, contable → guarda correctamente sin tocar el consecutivo.
* \[ ] Botón **"Ajustar Consecutivo"** (nuevo, separado de "Editar") abre el panel dedicado.
* \[ ] En el panel de ajuste: intentar poner un valor **menor** al actual → error en frontend antes de llamar al backend (*"El nuevo consecutivo no puede ser menor al actual"*).
* \[ ] Ajustar el consecutivo a un valor **mayor o igual** → éxito, la tabla refleja el nuevo valor.
* \[ ] Desactivar / Activar tipo de documento → funciona en ambos sentidos (antes solo existía "Desactivar" sin reactivar).

\---

## 6\. Plan de Cuentas (`ChartOfAccountsPage`)

**Carga de metadata dinámica**

* \[ ] Al abrir "Nuevo", los 3 selects (Clase, Categoría, Estado Financiero) cargan sus opciones desde el backend (`GET /v1/chart-of-accounts/metadata`), sin errores en consola.
* \[ ] Mientras cargan, los selects muestran "Cargando opciones..." y están deshabilitados.

**Cascada Clase → Categoría**

* \[ ] Elegir una Clase (ej. "Activo") → el select de Categoría se filtra a solo las categorías de esa clase.
* \[ ] Cambiar de Clase después de elegir una Categoría → la Categoría se resetea si ya no pertenece a la nueva clase.
* \[ ] Categoría muestra un texto de ayuda: "Mostrando categorías aplicables para: \[Clase elegida]".

**Estructura de código PUC**

* \[ ] Crear cuenta raíz (sin padre) con código de más de 1 dígito → error: *"Las cuentas raíz deben tener exactamente 1 dígito."*
* \[ ] Crear cuenta raíz con código de exactamente 1 dígito → éxito.
* \[ ] Crear cuenta hija cuyo código **no** empieza con el código del padre → error: *"El código de la subcuenta debe comenzar con el código de la cuenta padre."*
* \[ ] Crear cuenta hija con el salto de dígitos incorrecto (ej. padre de 1 dígito, hijo de 3 dígitos en vez de 2) → error: *"La estructura del código no es válida..."*
* \[ ] Marcar "Cuenta de Movimiento" = sí con un código de menos de 6 dígitos → error: *"Las cuentas de movimiento requieren al menos 6 dígitos."*
* \[ ] El hint bajo el campo "Código" (mientras se crea) muestra el número de dígitos esperado según el padre elegido, y se actualiza al cambiar de padre.

**Jerarquía y cuenta de movimiento**

* \[ ] El selector de "Cuenta Padre" no permite elegir la propia cuenta ni sus descendientes.
* \[ ] Editar una cuenta **con subcuentas**: el toggle "Cuenta de Movimiento" aparece deshabilitado con nota explicativa.
* \[ ] Elegir como padre una cuenta que ya es de movimiento → advertencia ámbar no bloqueante.
* \[ ] Intentar activar una cuenta cuyo padre está inactivo → error traducido: *"No se puede activar esta cuenta porque la cuenta padre está inactiva..."*

**Código inmutable**

* \[ ] Editar una cuenta existente → el campo "Código" aparece bloqueado (solo lectura), con nota explicativa.
* \[ ] Confirmar que el valor enviado al guardar sigue siendo el original, aunque se manipule el campo desde DevTools (defensa en profundidad).

**Reclasificación de clase**

* \[ ] Editar una cuenta y cambiarle la Clase contable (ej. de Activo a Pasivo) → el backend lo permite (comportamiento intencional), revisar el log del backend para confirmar el `WARN` de reclasificación.

**Activar / Desactivar**

* \[ ] Desactivar una cuenta con subcuentas → funciona (con warning en el log del backend), no bloquea.
* \[ ] Desactivar / Activar una cuenta normal → funciona en ambos sentidos.
* \[ ] Confirmar que el verbo HTTP usado es `DELETE` para desactivar (`/deactivate`) y `PATCH` para activar (`/activate`) — así quedó confirmado con el controller real.

\---

## 7\. Mensajes de error traducidos (todas las páginas)

* \[ ] Provocar un error de duplicado (ej. crear un centro de costo con código repetido) → mensaje traducido, no el genérico de Spring ni en inglés fijo.
* \[ ] Provocar un error de campo faltante a nivel de base de datos (si es posible) → mensaje traducido.
* \[ ] Cambiar el idioma de la UI a inglés y repetir alguna de las pruebas de error anteriores → el mensaje de error también cambia a inglés.
* \[ ] Si el backend devuelve un código de error que **no** está en `apiErrors.js` (código nuevo sin traducir) → no debe romper la UI; debe mostrar el mensaje crudo del backend como fallback.

\---

## 8\. Cosas transversales para revisar una sola vez

* \[ ] No hay errores en la consola del navegador al navegar entre todos los módulos del sidebar.
* \[ ] No hay warnings de React sobre `key` faltante, inputs no controlados, etc.
* \[ ] Todas las páginas muestran el nombre/id de la compañía correctamente en el `AppHeader`.
* \[ ] Ningún módulo quedó sin el `getApiErrorMessage` (ya confirmado por código: Terceros, Centros de Costo, Impuestos, Tipos de Documento, Plan de Cuentas — los 5 migrados).

\---

## Notas al marcar bugs encontrados

Para cada ítem que falle, anota:

1. Módulo y acción exacta.
2. Mensaje de error (si lo hay) — texto completo de consola y/o toast.
3. Petición de red relevante (método, URL, status code, response body) si aplica.

Eso permite diagnosticar sin repetir todo el ciclo de preguntas de esta sesión.

