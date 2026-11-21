# Auditoría arquitectónica — Módulo Contable ERP-CLOUD (modo desafío) — v2, verificada contra el código

| Campo | Valor |
|---|---|
| Fecha | 2026-11-19 (v2, misma noche) |
| Código revisado | `erp-cloud`@`java17` HEAD `4bf4877` (200 archivos Java, migraciones V1–V6, `infra/terraform`) y `erp-cloud-frontend`@`main` |
| Método | Lectura directa de servicios, entidades, repositorios, filtros de seguridad, migraciones y Terraform. Escaneo automático de las 48 consultas `@Query` y de todos los `findById` de la capa de servicio |
| Diferencia con la v1 | La v1 salió solo de la documentación. Esta versión marca cada hallazgo como **CONFIRMADO**, **PARCIAL** (una parte ya está resuelta en el código), **DESCARTADO** (el código lo desmiente) o **NUEVO** (no estaba en la v1) |
| Severidad | **[CRÍTICO]**: pérdida o fuga de datos, o libros sin validez probatoria; bloquea producción multi-cliente. **[ALTO]**: se rompe con carga real o con el siguiente módulo. **[MEDIO]**: deuda que encarece cada módulo nuevo |

Las rutas se citan relativas a `src/main/java/com/erp/erp_cloud/` (backend) o `src/` (frontend), salvo que se indique otra cosa.

---

## 0. Tabla de riesgos

| ID | Frente | Hallazgo | Estado | Severidad |
|---|---|---|---|---|
| F1-01 | Integridad | Anular pone las líneas en cero y reescribe la descripción | CONFIRMADO | **[CRÍTICO]** |
| F1-02 | Integridad | Edición en sitio que borra y recrea renglones; sin Envers en comprobantes; `created_by`/`updated_by` siempre = 1 | CONFIRMADO, peor de lo supuesto | **[CRÍTICO]** |
| F1-25 | Integridad | Libro Diario y otros reportes truncados en silencio a 2.000 comprobantes; selectores de cuentas y terceros cortados en 1.000 | **NUEVO** | **[CRÍTICO]** |
| F1-03 | Integridad | Sin idempotencia; `Ctrl+Enter` repetido durante un guardado envía dos POST | CONFIRMADO | **[CRÍTICO]** |
| F1-20 | Concurrencia | No hay un solo `@Version` en el proyecto: actualización perdida | CONFIRMADO | **[CRÍTICO]** |
| F1-13 | Multitenant | `POST /api/auth/companies` devuelve id, razón social, nombre comercial y NIT de las empresas de cualquier correo, sin autenticar | CONFIRMADO | **[CRÍTICO]** |
| F1-26 | Seguridad | Rate limit evadible falsificando `X-Forwarded-For`; el bloqueo por correo permite bloquear cuentas ajenas | **NUEVO** | [ALTO] |
| F1-27 | Seguridad | La tabla `permissions` se carga en el token pero ningún endpoint la exige; un `ACCOUNTANT` puede cambiar montos con `PUT` aunque anular exige admin | **NUEVO** | [ALTO] |
| F1-06 | Integridad | Cierre de período sin bloqueo de fila (check-then-act) | CONFIRMADO | [ALTO] |
| F1-07 | Integridad | `account_opening_balances` sin ningún lector | CONFIRMADO | [ALTO] |
| F1-11 | Multitenant | Aislamiento por convención; las consultas están bien, pero no hay prueba de extremo a extremo | PARCIAL | [ALTO] (era CRÍTICO) |
| F1-16 | Multitenant | `TenantContext` en `ThreadLocal`; hoy no hay `@Async` ni `@Scheduled`, el riesgo nace con los eventos | CONFIRMADO (latente) | [ALTO] |
| F1-21 | Rendimiento | N+1 en el listado de comprobantes; pool de 5 contra 80 peticiones concurrentes por instancia | CONFIRMADO | [ALTO] |
| F1-22 | Concurrencia | El candado del consecutivo se toma al inicio y se sostiene toda la transacción | CONFIRMADO | [ALTO] |
| F1-23 | Seguridad | Spring Boot 3.3.2 sin parches de seguridad | CONFIRMADO | [ALTO] |
| F1-04 | Integridad | Consecutivo: el bloqueo pesimista existe; queda `is_active` dentro de la llave única | PARCIAL | [MEDIO] (era ALTO) |
| F1-05 | Integridad | Backend correcto (`DECIMAL(10,4)`, `HALF_UP`, `compareTo`); el frontend calcula con `parseFloat`; motor de impuestos duplicado | PARCIAL | [MEDIO] (era ALTO) |
| F1-08 | Integridad | `'CIERRE'` literal en JPQL; un usuario puede crear el tipo `CIERRE`/`APERTURA` antes que el sistema | CONFIRMADO | [MEDIO] |
| F1-09 | Integridad | Importación de saldos sin identidad del archivo | Sin cambio (diseño) | [MEDIO] |
| F1-10 | Integridad | `abs(base)` en el cálculo del impuesto: no se puede expresar una devolución | CONFIRMADO | [MEDIO] |
| F1-12 | Multitenant | Renglones con FKs compuestas por `company_id`; quedan 10 FKs simples en otras tablas | PARCIAL | [MEDIO] (era CRÍTICO) |
| F1-15 | Multitenant | Revocación de acceso inmediata; quedan JWT de 8 h en `localStorage` y roles retirados vigentes hasta el siguiente login | PARCIAL | [MEDIO] (era ALTO) |
| F1-17 | Seguridad | Cuerpo y pie de las plantillas PDF se insertan sin escapar y openhtmltopdf resuelve URLs externas | PARCIAL (logo y XSS descartados) | [MEDIO] (era ALTO) |
| F1-18 | Multitenant | Sin restauración por compañía; `revinfo` sin `company_id` | CONFIRMADO | [MEDIO] |
| F1-28 | Multitenant | `/api/v1/users/lookup` revela el nombre de cualquier usuario del sistema a cualquier admin | **NUEVO** | [MEDIO] |
| F1-19 | Seguridad | Inyección de fórmulas en CSV y Excel | No verificado en esta pasada | [MEDIO] |
| F1-14 | Multitenant | Respaldo `X-Tenant-Id` en rutas públicas | **DESCARTADO** (eliminado el 2026-11-02) | — |
| F2-01 | Acoplamiento | `journal_entries` no guarda documento de origen | CONFIRMADO | **[CRÍTICO]** |
| F2-06 | Eventos | Cero infraestructura asíncrona (ni `@Async`, ni `@Scheduled`, ni outbox, ni cola) | CONFIRMADO | **[CRÍTICO]** |
| F2-09 | Modelo transversal | Sin moneda en el dominio | CONFIRMADO | **[CRÍTICO]** |
| F2-02 | Acoplamiento | Dos caminos de escritura (validado y directo) + motor de impuestos duplicado | CONFIRMADO | [ALTO] |
| F2-03 | Acoplamiento | Impuestos: `UNIQUE (company_id, account_id)`, sin vigencias ni código DIAN | CONFIRMADO | [ALTO] |
| F2-04 | Acoplamiento | La anulación por ceros es incompatible con la nota crédito electrónica | CONFIRMADO | [ALTO] |
| F2-07 | Eventos | Reportes O(historial completo) | CONFIRMADO | [ALTO] |
| F2-08 | Eventos | Granularidad de los asientos automáticos sin definir | Diseño | [ALTO] |
| F2-10 | Modelo transversal | Terceros sin roles ni responsabilidades RUT | CONFIRMADO | [ALTO] |
| F2-11 | Modelo transversal | Sin partidas abiertas | CONFIRMADO | [ALTO] |
| F2-05 | Acoplamiento | `Company` EAGER guardada en `ThreadLocal` | CONFIRMADO | [MEDIO] |
| F2-12 | Modelo transversal | Sin documento soporte ni rangos de numeración DIAN | CONFIRMADO | [MEDIO] |
| F2-13 | Modelo transversal | Una sola dimensión analítica | CONFIRMADO | [MEDIO] |
| T-01 | Cumplimiento | Sin bitácora, autoría real ni segregación de funciones | CONFIRMADO | **[CRÍTICO]** |
| T-02 | Negocio | No hay nivel "firma contable" | Diseño | [ALTO] |
| T-03 | Negocio | Sin exportación, retención ni baja por cliente | Diseño | [ALTO] |
| T-04 | Negocio | Picos de vencimientos DIAN contra la infraestructura del piloto | CONFIRMADO en Terraform | [ALTO] |

**Lo que el código desmintió, para no perder tiempo en ello:** las consultas de reportes **sí** filtran por compañía; los renglones **sí** tienen llaves foráneas compuestas; el consecutivo **sí** usa bloqueo pesimista; el backend **sí** redondea con una política explícita y compara con `compareTo`; el respaldo `X-Tenant-Id` ya no existe; la revocación de acceso a una compañía es inmediata; el logo no es una URL; la vista previa del certificado está en un `iframe` con `sandbox=""`. Casi todo eso salió de la auditoría del 2026-11-02 (`progreso-2026-11-02.md`). Los problemas que quedan son de otra naturaleza: **inmutabilidad, autoría, completitud de los reportes y preparación para módulos.**

---

## FRENTE 1 — Módulo contable actual

### 1.1 Integridad y motor contable

#### F1-01 [CRÍTICO] Anular destruye el registro original — CONFIRMADO

**Evidencia.** `service/JournalEntryService.java:319-341`: `annul()` hace `item.setDebit(ZERO)` y `item.setCredit(ZERO)` en cada renglón y antepone `[ANNULLED] ` a la descripción. `AccountingPeriodService.java:594` y `SiewinBalanceImportService.java:477` hacen lo mismo con CIERRE y APERTURA. No hay Envers en `JournalEntry` ni en `JournalEntryItem` (ver F1-02), así que **los montos originales desaparecen de la base de datos**: no quedan ni en una tabla de auditoría.

**Escenario.** CE-120 de marzo por $8.000.000 entró en la declaración de IVA. En junio un admin lo anula. El Libro Diario de marzo, impreso de nuevo, ya no tiene esos $8.000.000, y no existe ningún registro, en ninguna tabla, de que alguna vez los tuvo. Solo quedan `annulled_at` y `annulment_reason`.

**Cambio de diseño obligatorio, hoy:**
- [ ] Estados `DRAFT → POSTED → REVERSED`. Anular un `POSTED` = **comprobante de reverso** (renglones espejo) enlazado por `reverses_entry_id`. El original no se toca, salvo su `status`.
- [ ] Mismo patrón en `AccountingPeriodService` (recálculo de CIERRE, `reopenYear`) y en `SiewinBalanceImportService` (reemplazo de APERTURA).
- [ ] Revisar los reportes que hoy dependen de "anulado = ceros". `findTaxItems` filtra `annulled = false`; el resto suma los ceros.
- [ ] Trigger MySQL `BEFORE UPDATE` en `journal_entry_items` que rechace cambios de montos, cuenta, tercero, centro de costo, base o impuesto si la cabecera está `POSTED`, y `BEFORE DELETE` que rechace el borrado.

```sql
ALTER TABLE journal_entries
  ADD COLUMN status ENUM('DRAFT','POSTED','REVERSED') NOT NULL DEFAULT 'POSTED',
  ADD COLUMN reverses_entry_id BIGINT NULL,
  ADD COLUMN reversed_by_entry_id BIGINT NULL,
  ADD COLUMN posted_at DATETIME(6) NULL,
  ADD COLUMN posted_by BIGINT NULL;
```

#### F1-02 [CRÍTICO] Editar borra el pasado y nadie firma nada — CONFIRMADO, peor de lo supuesto

**Evidencia.**
1. `service/JournalEntryService.java:489-490`: `update()` hace `existingEntry.getItems().clear()` y reconstruye los renglones. Con `orphanRemoval = true` (`entity/JournalEntry.java:86`) eso **ejecuta `DELETE` de los renglones originales** e inserta nuevos con ids nuevos. Se permite en cualquier comprobante de un período abierto, APERTURA incluido (decisión del 2026-11-19, líneas 462-468).
2. Envers solo está en `User`, `Role`, `UserRole` y `LegalDocumentTemplate`. **`JournalEntry` y `JournalEntryItem` no tienen `@Audited`.** La v1 suponía que "al menos está Envers": no está.
3. `config/JpaConfig.java`: `AuditorAware` devuelve `Optional.of(1L)` fijo (marcado `FIXME: TEMPORARY BOOTSTRAP PHASE`). **Todo `created_by` y `updated_by` de todas las tablas vale 1**, sin importar quién operó.
4. `annul()` manual nunca llama `setAnnulledBy()`: solo lo hacen los procesos de sistema. El campo `annulled_by` de una anulación hecha por una persona queda `NULL`.

**Por qué es crítico.** Hoy el sistema no puede responder **quién** creó, editó o anuló ningún comprobante, ni **qué decía antes**. Para un revisor fiscal, para la DIAN o en un litigio, eso deja a los libros sin valor probatorio.

**Cambio de diseño obligatorio, hoy:**
- [ ] `JpaConfig`: `AuditorAware` que lea `UserPrincipal.getId()` del `SecurityContextHolder`, con un usuario técnico `SYSTEM` explícito solo para los procesos de sistema. Una línea de código. Es el arreglo de mayor valor por minuto de trabajo de toda esta auditoría.
- [ ] `annul()` registra `annulledBy` con el usuario real.
- [ ] Con F1-01: `POSTED` no se edita. La UI puede ofrecer "editar", pero por debajo el backend hace reverso + comprobante nuevo enlazado. Revertir APERTURA editable o convertirlo en reverso parcial.
- [ ] Tabla `audit_log` append-only (sin `UPDATE`/`DELETE` para `erp_app`) escrita en la misma transacción, con `company_id`, `user_id`, `ts`, `entity`, `entity_id`, `action`, `before_json`, `after_json`, `reason`, `request_id` e `ip`. Como mínimo inmediato, `@Audited` en `JournalEntry`, `JournalEntryItem`, `AccountingPeriod`, `ChartOfAccounts`, `Tax`, `ThirdParty` y `DocumentType`, con `company_id` en `revinfo`.

#### F1-25 [CRÍTICO] Reportes y selectores truncados en silencio — NUEVO

**Evidencia.**
- No hay `spring.data.web.pageable.max-page-size` en ninguna configuración, así que rige el valor por defecto de Spring Data: **2.000**. El frontend pide `size: 10000` y usa `content` sin comparar con `totalElements`:
  - Libro Diario: `components/pages/JournalBookPage.jsx:223`
  - Búsqueda por valor: `components/pages/ValueSearchPage.jsx:283`
  - Resumen por tipo de comprobante: `components/pages/VoucherTypeSummaryPage.jsx:240`
  - Export del listado de comprobantes: `components/pages/JournalEntryPage.jsx:485`
- Catálogos con `size: 1000`: plan de cuentas y terceros en `JournalEntryPage.jsx:503-504`, plan de cuentas en `TaxPage.jsx:220`, terceros en `TaxDetailPage.jsx:144` y `ThirdPartyBalancePage.jsx:147`.

**Escenario.** Una compañía con 2.500 comprobantes en el año saca el Libro Diario anual: salen 2.000, con totales que cuadran consigo mismos y **sin ningún aviso**. Es un libro oficial incompleto. Una compañía que importa el PUC COMERCIAL (2.275 cuentas según `progreso-2026-10-30.md`) no encuentra en el selector del comprobante las cuentas que quedaron después de la posición 1.000. Una copropiedad con más de 1.000 terceros no puede seleccionar al tercero 1.001.

**Cambio de diseño obligatorio, hoy:**
- [ ] Cualquier pantalla que pida "todo" compara `content.length` con `totalElements` y **se niega a mostrar el reporte** si no coinciden. Es un parche mínimo, para hoy mismo.
- [ ] Libro Diario, Búsqueda por valor y exports pasan a un endpoint propio que transmite por streaming (`StreamingResponseBody` con cursor y `fetchSize`), o que pagina y deja que el frontend recorra todas las páginas.
- [ ] Selectores de cuenta y tercero con búsqueda en el servidor (`?q=`, top 50), no con el catálogo entero en memoria.
- [ ] Fijar `spring.data.web.pageable.max-page-size=500` de forma explícita, para que el límite sea una decisión y no un accidente.

#### F1-03 [CRÍTICO] Doble envío sin idempotencia — CONFIRMADO

**Evidencia.** No existe ninguna referencia a idempotencia en el backend. En el frontend, `handleSave` (`JournalEntryPage.jsx:1123-1158`) no consulta el estado `saving` antes de enviar. El atajo `Ctrl+Enter` llama `formRef.current.requestSubmit()` (línea 1876), que dispara el envío aunque el botón Guardar esté deshabilitado. El `<fieldset>` solo se deshabilita cuando el período está cerrado.

**Escenario.** El backend tarda por arranque en frío y el digitador presiona `Ctrl+Enter` otra vez: se crean dos comprobantes con consecutivos seguidos.

- [ ] Arreglo de hoy: `if (saving) return;` al inicio de `handleSave`.
- [ ] Cabecera `Idempotency-Key` (UUID generado al abrir el formulario) con `UNIQUE (company_id, idempotency_key)` en `journal_entries`. Un segundo POST con la misma llave devuelve el comprobante ya creado. Es el mismo contrato que exige F2-01.

#### F1-04 [MEDIO] Consecutivo — PARCIAL (era ALTO)

**Lo que está bien.** `DocumentTypeRepository.java:39-53` usa `@Lock(PESSIMISTIC_WRITE)`, y `DocumentTypeService.getNextConsecutive()` (líneas 137-155) incrementa dentro de la misma transacción del comprobante. Un rollback deshace también el incremento: no hay huecos ni colisiones por concurrencia.

**Lo que queda.**
- [ ] `V1__baseline.sql:224`: `uk_company_doctype_consecutive_active` incluye `is_active`, así que el mismo consecutivo puede existir dos veces si uno está inactivo. Cambiar a `UNIQUE (company_id, document_type_id, consecutive)`.
- [ ] Para facturación: `numbering_ranges` (prefijo, rango, resolución DIAN, vigencia) separado del consecutivo interno.
- [ ] El costo de rendimiento de este candado está en F1-22.

#### F1-05 [MEDIO] Precisión y redondeo — PARCIAL (era ALTO)

**Lo que está bien.** `Tax.rate` es `DECIMAL(10,4)` (`entity/Tax.java:60`): admite 0,9660 % para ReteICA. `handleTax()` usa `divide(100, 2, HALF_UP)` (`JournalEntryService.java:685`). El balance se valida con `compareTo` (línea 132) y los montos se escalan a 2 con `HALF_UP` (línea 276).

**Lo que queda.**
- [ ] El frontend calcula impuestos y totales con `parseFloat` (`JournalEntryPage.jsx:68`, `94-106`, `1084-1098`). La previsualización "Balanceado" puede contradecir al servidor en sumas largas. Usar enteros en centavos o `decimal.js`.
- [ ] `service/AccountingEngineService.java` es un segundo motor de impuestos, sin uso hoy, con semántica distinta (no aplica `abs`, y lanza `NullPointerException` si `minimumBase` es `null`). Borrarlo antes de que un módulo nuevo lo encuentre y lo use.
- [ ] Documentar el redondeo por renglón como política (`MoneyPolicy`) y probarlo con tarifas reales.

#### F1-06 [ALTO] Cierre de período sin bloqueo — CONFIRMADO

**Evidencia.** Ningún método de `AccountingPeriodRepository` tiene `@Lock`; los únicos repositorios con bloqueos son `DocumentTypeRepository` y `TaxRepository`. `validateDateIsOpen()` (`AccountingPeriodService.java:771`) lee el período sin bloquearlo. Cierre y reapertura exigen admin (`AccountingPeriodController.java:101-177`), pero el mismo admin puede cerrar, reabrir, anular y volver a cerrar.

- [ ] Contabilizar toma `LOCK IN SHARE MODE` sobre la fila del período y cerrar toma `FOR UPDATE`.
- [ ] Permiso `PERIOD_REOPEN` separado de `PERIOD_CLOSE`, con `audit_log` (depende de F1-27).

#### F1-07 [ALTO] Snapshot de saldos sin lector — CONFIRMADO

**Evidencia.** `AccountOpeningBalanceRepository` solo se usa en `AccountingPeriodService`. Los reportes suman el histórico completo (`ChartOfAccountsRepository.java:189-266`, filtros `je.entryDate < :startDate` sin límite inferior).
- [ ] Usarla (con conciliación automática contra la suma en vivo) o eliminarla. Se resuelve con F2-07.

#### F1-08 [MEDIO] Tipos reservados por cadena — CONFIRMADO

**Evidencia.** `ChartOfAccountsRepository.java:330` filtra con `je.documentType.code <> 'CIERRE'`. `DocumentTypeService.create()` (línea 59) solo rechaza códigos duplicados. Un usuario puede crear un tipo `CIERRE` o `APERTURA` antes del primer cierre o de la primera migración, y `findOrCreate…()` (líneas 216 y 235) lo reutilizará como si fuera del sistema.
- [ ] Columna `system_kind` en `document_types`. Rechazar los códigos reservados en `create()` y `update()`, y filtrar por `system_kind`, no por `code`.

#### F1-09 [MEDIO] Importación de saldos sin identidad del archivo — sin cambio

- [ ] Guardar en APERTURA el hash SHA-256, el nombre del archivo, el usuario y la fecha. Confirmar NIT y nombre de la compañía destino antes de migrar.

#### F1-10 [MEDIO] `abs(base)` — CONFIRMADO

`handleTax()` calcula sobre `absBase` y elige el lado por `tax.sign`. No hay forma de expresar una devolución o una nota crédito con impuesto negativo.
- [ ] Definir el modelo de reverso de impuestos con F2-03 y F2-04.

---

### 1.2 Aislamiento multitenant

#### F1-13 [CRÍTICO] Directorio público de clientes — CONFIRMADO

**Evidencia.** `security/AuthController.java:130-141`: `POST /api/auth/companies` (público, `SecurityConfig.java:76`) devuelve `CompanyOptionDTO(id, legalName, tradeName, taxId)` de **todas** las compañías a las que tiene acceso el correo enviado. El comentario dice que devolver lista vacía para un correo desconocido evita la enumeración. Es al revés: una lista **no vacía** confirma que el correo existe y además entrega la cartera de clientes de ese contador con NIT. El límite es de 20 consultas por minuto por IP, y la IP se puede falsificar (F1-26).

**Escenario.** Alguien prueba `contabilidad@`, `contador@` y `revisoria@` sobre los dominios de firmas contables de la ciudad, y en una tarde arma la lista de qué firma lleva a qué empresa, con el NIT de cada una. Insumo perfecto para phishing ("su contador le envía la factura…").

**Cambio de diseño obligatorio, hoy:**
- [ ] Login en dos pasos: (1) correo + contraseña → token de pre-sesión cuyo único alcance es `GET /api/auth/my-companies`; (2) elegir compañía → token final. Eliminar `POST /api/auth/companies`.
- [ ] Mientras tanto, como mínimo: no devolver `taxId` ni `tradeName` y exigir la contraseña en la misma petición.

#### F1-11 [ALTO] Aislamiento por convención, sin prueba — PARCIAL (era CRÍTICO)

**Lo que está bien.** Escaneé las 48 consultas `@Query` de los repositorios. Todas filtran por compañía, salvo 7 que reciben como parámetro una entidad que ya pertenece al tenant (`existsByAccount(account)`, `existsByThirdParty(tp)`, `findByIdWithLock`, seguido de un filtro). Todos los `findById` de la capa de servicio están seguidos de `.filter(x -> x.getCompany().getId().equals(companyId))`. El `TenantFilter` toma la compañía solo del JWT y revalida el acceso en cada petición (`security/filter/TenantFilter.java:54-77`).

**Lo que queda.** El aislamiento depende de que cada desarrollador recuerde poner ese `.filter()` o ese `AND company.id`. Las pruebas que mencionan otra compañía son unitarias con mocks (`JournalEntryServiceTest`, `TaxServiceTest`, etc.). **No hay ninguna prueba de extremo a extremo con dos compañías reales en una base real.** Cada módulo nuevo agrega decenas de consultas.
- [ ] Prueba de integración en CI (MySQL real, ya disponible en el pipeline): sembrar A y B, recorrer todos los GET autenticado como A y fallar si aparece cualquier identificador o nombre de B. La lista de endpoints se obtiene por reflexión, para que un endpoint nuevo entre solo.
- [ ] Regla ArchUnit: prohibido llamar `JpaRepository.findById` de una entidad con `company` desde un servicio; usar `findByIdAndCompanyId`.

#### F1-16 [ALTO] `ThreadLocal` sin propagación — CONFIRMADO (latente)

**Evidencia.** `security/context/TenantContext.java:15-16`; se limpia en `TenantFilter.java:99`. Hoy no existe ningún `@Async`, `@Scheduled` ni `Executor` en el código, así que el riesgo **todavía no se manifiesta**: nace el día que entren los eventos (F2-06).
- [ ] El `companyId` viaja explícito en cada mensaje o job, y se restaura con un `TaskDecorator` que limpia en `finally`. Si el contexto está vacío, se lanza excepción; nunca hay un valor por defecto.

#### F1-12 [MEDIO] Llaves foráneas — PARCIAL (era CRÍTICO)

**Lo que está bien.** `journal_entry_items` tiene `company_id` y FKs compuestas contra cuenta, centro de costo, cabecera, impuesto y tercero (`db/migration/V1__baseline.sql:264-268`).

**Lo que queda: FKs simples** que permiten, a nivel de base de datos, referencias cruzadas entre compañías (hoy las frena solo el servicio):
- [ ] `journal_entries.document_type_id` (V1:230)
- [ ] `document_types.default_account_id` (V1:199)
- [ ] `taxes.account_id` (V1:400)
- [ ] `accounting_periods.gain_account_id` y `loss_account_id` (V1:72-73)
- [ ] `t_companies.default_gain_account_id` y `default_loss_account_id` (V1:371-372)
- [ ] `third_parties.cost_center_id` (V1:443)
- [ ] `chart_of_accounts.parent_id` y `cost_centers.parent_id` (V1:123, 158)
- [ ] `user_roles (company_id, role_id)` (V1:463-464): la base acepta un rol de la compañía B en una asignación de la compañía A. Requiere FK compuesta, o un trigger que lo verifique, porque los roles globales tienen `company_id NULL`.

#### F1-15 [MEDIO] Tokens — PARCIAL (era ALTO)

**Lo que está bien.** La pertenencia del usuario a la compañía se revalida en cada petición (`TenantFilter.java:69-77`). El secreto JWT exige al menos 32 bytes al arrancar (`JwtTokenProvider.java:43-46`). Swagger está apagado en producción (`application-prod.properties`).

**Lo que queda.**
- [ ] JWT de 8 h en `localStorage` (`context/AuthContext.jsx:32`, `79`): pasar a access token corto + refresh token en cookie `HttpOnly`.
- [ ] Un rol retirado sigue en el token hasta el siguiente login (reconocido en `progreso-2026-11-02.md`). Releer las autoridades por petición o usar `token_version`.
- [ ] CSP estricta cuando se cree `firebase.json`.

#### F1-17 [MEDIO] Plantillas PDF — PARCIAL (era ALTO)

**Descartado.** El logo es un data URL validado en `CompanyService`, no una URL. La vista previa se muestra en `<iframe srcDoc sandbox="">` (`WithholdingCertificatePage.jsx:479-483`), así que no hay XSS.

**Confirmado.** `resources/templates/certificates/certificate-skeleton.html:87,124` inserta `{{{resolved_body}}}` y `{{{resolved_footer}}}` sin escapar. `WithholdingCertificateService.java:382-385` construye el `PdfRendererBuilder` sin `useUriResolver` ni un `HttpStreamFactory` propio, así que openhtmltopdf intenta descargar cualquier `<img src>` o `<link href>`. Un admin de **cualquier** compañía (es decir, cualquier cliente del SaaS) puede hacer que el servidor haga peticiones a hosts internos de la VPC: SSRF ciega. El servidor de metadatos de GCP exige la cabecera `Metadata-Flavor`, que un `<img>` no envía, así que el robo de tokens de la cuenta de servicio es poco probable. Por eso baja a MEDIO.
- [ ] `useUriResolver` que solo acepte `data:` y recursos del classpath, y rechace todo lo demás.

#### F1-18 [MEDIO] Restauración por compañía y `revinfo` — CONFIRMADO

- [ ] PITR restaura la base completa. Escribir y probar un procedimiento de restauración de una sola compañía (restaurar en una base temporal, copiar `company_id = X`) en el `restore_drill`.
- [ ] Agregar `company_id` a `revinfo` cuando Envers se extienda a las entidades contables (F1-02).

#### F1-28 [MEDIO] Directorio global de usuarios — NUEVO

**Evidencia.** `service/UserManagementService.java:92-96`: `lookupByEmail` responde `exists` y `fullName` para **cualquier** correo registrado en el sistema, a cualquier admin de cualquier compañía. Combinado con F1-13, el admin de una compañía cualquiera puede identificar por nombre a los usuarios de otros clientes.
- [ ] Devolver solo `exists` (sin nombre) o requerir que el usuario invitado acepte la invitación.

#### F1-19 [MEDIO] Inyección de fórmulas — no verificado en esta pasada

- [ ] Prefijar con `'` toda celda de texto que empiece por `=`, `+`, `-`, `@`, tabulador o retorno en `downloadIssuesCsv` y en los exports de SheetJS.

---

### 1.3 Seguridad y control de acceso

#### F1-26 [ALTO] Rate limit evadible; bloqueo de cuentas ajenas — NUEVO

**Evidencia.** `security/filter/AuthRateLimitFilter.java:119-126` toma el **primer** valor de `X-Forwarded-For` cuando `trust-forwarded-for=true`, que es el valor por defecto en producción (`application-prod.properties`). El primer valor lo escribe el cliente: Firebase Hosting y Cloud Run agregan la IP real al **final**. Cambiar la cabecera en cada petición elimina el límite. Además, el mapa vive en memoria y se borra en cada arranque en frío.

Por su parte, `LoginAttemptService` bloquea **la cuenta** 15 minutos tras 5 fallos. Sin límite por IP efectivo, cualquiera puede mantener bloqueado al contador de un cliente indefinidamente con 5 intentos cada 15 minutos.
- [ ] Usar la IP que agrega la infraestructura (el **último** salto confiable) o `request.getRemoteAddr()` con `server.forward-headers-strategy=framework` y la lista de proxies de confianza configurada.
- [ ] Mover el contador a Memorystore o Cloud Armor (permite además quitar `max_instance_count = 1`).
- [ ] Bloqueo progresivo con CAPTCHA en lugar de un bloqueo duro de la cuenta.

#### F1-27 [ALTO] Los permisos no se aplican — NUEVO

**Evidencia.** `CustomUserDetailsService.java:95` carga los permisos del rol como autoridades, pero no existe ningún `hasAuthority` ni `hasPermission` en el código. El único control es `@authz.isAdmin()`, presente en 10 de los 18 controladores. `PUT /api/v1/journal-entries/{id}` (`JournalEntryController.java:128`) **no** exige admin; anular (`:309`) sí lo exige.

**Consecuencia.** Un `ACCOUNTANT` no puede anular un comprobante, pero sí puede abrirlo, poner todos los montos en otra cuenta o cambiar su fecha, con el mismo efecto contable y sin rastro (F1-02). La tabla `permissions` es decorativa.
- [ ] Matriz mínima aplicada con `@PreAuthorize("hasAuthority('…')")`: `JOURNAL_CREATE`, `JOURNAL_EDIT_DRAFT`, `JOURNAL_REVERSE`, `PERIOD_CLOSE`, `PERIOD_REOPEN`, `MASTER_DATA_EDIT`, `IMPORT_RUN`, `COA_RESET`, `REPORT_VIEW`, `USER_ADMIN`.
- [ ] Prueba automatizada que falle si un endpoint `POST/PUT/PATCH/DELETE` no declara su permiso.

#### F1-23 [ALTO] Plataforma sin parches — CONFIRMADO

`build.gradle:4`: `org.springframework.boot` `3.3.2`, sin soporte OSS desde junio de 2025 ni soporte comercial desde junio de 2026.
- [ ] Actualizar a 3.5.x **antes** de abrir a más clientes.

---

### 1.4 Concurrencia y rendimiento

#### F1-20 [CRÍTICO] Actualización perdida — CONFIRMADO

**Evidencia.** No hay `@Version` en ninguna entidad del proyecto.

**Escenario.** El auxiliar y el contador abren el mismo comprobante. El auxiliar guarda una corrección de tercero; el contador guarda una de monto un minuto después. Gana el contador, y como `update()` borra y recrea los renglones (F1-02), la corrección del auxiliar desaparece sin dejar rastro.
- [ ] `@Version` en `JournalEntry`, `AccountingPeriod`, `ChartOfAccounts`, `Tax`, `ThirdParty`, `CostCenter`, `DocumentType` y `Company`. El frontend envía la versión y el backend responde 409 si cambió.

#### F1-21 [ALTO] N+1 y pool desproporcionado — CONFIRMADO

**Evidencia.**
- `repository/JournalEntryRepository.java:43-46`: `searchEntries` solo hace `JOIN FETCH j.documentType`. `mapToResponseDTO()` recorre los renglones, que son LAZY (`entity/JournalEntryItem.java:23-71`), y de cada uno lee cuenta, tercero, centro de costo e impuesto. Sin `hibernate.default_batch_fetch_size`. Una página de 2.000 comprobantes ≈ **2.001 consultas para los renglones**, más las de cada cuenta, tercero, centro de costo e impuesto distinto.
- `infra/terraform/run.tf:8,26,31`: `DB_POOL_SIZE=5`, `max_instance_request_concurrency=80`, `max_instance_count=1`, sobre `db-f1-micro`. Hasta 80 peticiones compiten por 5 conexiones.

**Escenario.** Un usuario genera el Libro Diario anual (miles de consultas sobre una conexión por varios segundos). Cuatro más hacen lo mismo y la compañía de al lado no puede guardar un comprobante: el pool está agotado para **todos los clientes**.
- [ ] `spring.jpa.properties.hibernate.default_batch_fetch_size=100` como primer arreglo (una línea).
- [ ] Proyección DTO con una sola consulta para listados y reportes (`SELECT new …` con `JOIN`), sin hidratar entidades.
- [ ] Bajar `max_instance_request_concurrency` a algo proporcional al pool (p. ej. 10), y crear un pool separado y pequeño para reportes, con `jakarta.persistence.query.timeout`.
- [ ] Exports grandes asíncronos o por streaming (con F1-25).

#### F1-22 [ALTO] Candado del consecutivo sostenido toda la transacción — CONFIRMADO

**Evidencia.** `JournalEntryService.create()` toma el candado en el paso 4 (`createEntryHeader` → `getNextConsecutive`, líneas 88 y 207-208). **Después** valida cada renglón (`processLineItems`, que consulta cuenta, tercero, centro de costo e impuesto por línea) y guarda. La fila de `document_types` queda bloqueada hasta el commit.

**Consecuencia.** Todos los usuarios que crean comprobantes del mismo tipo en la misma compañía quedan en fila uno detrás de otro, durante toda la validación. Con asientos automáticos masivos (factura, recibo, nómina, todos con su tipo), el tipo de documento se vuelve el embudo de cada módulo.
- [ ] Mover la asignación del consecutivo al **final**, justo antes de `save()`, después de todas las validaciones.
- [ ] Con F2-06: asignar los consecutivos de un lote de asientos automáticos con un solo `UPDATE … SET current_consecutive = current_consecutive + N`.

---

## FRENTE 2 — Prueba de escalabilidad hacia los módulos futuros

Todo lo de esta sección se confirmó por **ausencia** en el código: no hay ninguna referencia a `source_module`, outbox, moneda de dominio, `@Async` ni `@Scheduled`. El diseño y los cambios propuestos en la v1 siguen vigentes; aquí se resumen con la evidencia exacta.

### 2.1 Acoplamiento y deuda técnica

#### F2-01 [CRÍTICO] El asiento no sabe de qué documento vino — CONFIRMADO

`journal_entries` (V1:205-231) no tiene columnas de origen. Sin ellas no hay forma de evitar la doble contabilización de una factura, de navegar factura ↔ asiento, de reversar desde el módulo, ni de impedir la edición manual de un asiento automático.
```sql
ALTER TABLE journal_entries
  ADD COLUMN origin ENUM('MANUAL','SYSTEM','MODULE') NOT NULL DEFAULT 'MANUAL',
  ADD COLUMN source_module VARCHAR(30) NULL,
  ADD COLUMN source_document_id VARCHAR(64) NULL,
  ADD COLUMN source_event_type VARCHAR(40) NULL,
  ADD COLUMN source_version INT NULL,
  ADD UNIQUE KEY uk_je_source (company_id, source_module, source_document_id, source_event_type, source_version);
```
- [ ] Los asientos `origin = 'MODULE'` no se editan ni se reversan desde Contabilidad.
- [ ] Reporte de conciliación subledger ↔ cuenta de control.

#### F2-02 [ALTO] Dos caminos de escritura y un motor de impuestos duplicado — CONFIRMADO

`create()` valida y contabiliza. `AccountingPeriodService` y `SiewinBalanceImportService` guardan directo en el repositorio, saltándose las invariantes. `AccountingEngineService` repite el cálculo de impuestos con otra semántica (F1-05).
- [ ] `LedgerPostingService` como **único** punto de escritura, que garantiza balance, período abierto, cuentas de movimiento de la misma compañía, dimensiones requeridas, secuencia, idempotencia e inmutabilidad. Encima: `ManualJournalService` (reglas de digitación) y un adaptador por módulo. Regla ArchUnit: nadie más usa `JournalEntryRepository.save`.

#### F2-03 [ALTO] Modelo de impuestos — CONFIRMADO

`V1:397` `uk_tax_company_account (company_id, account_id)`: un impuesto por cuenta. `handleTax()` deriva el impuesto de la cuenta del renglón (un impuesto por renglón). `Tax` no tiene vigencias ni código de tributo DIAN.
- [ ] `tax_rates` con `valid_from` y `valid_to`; el renglón guarda la **tarifa aplicada**, no solo `tax_id`; `dian_tax_code` obligatorio; N impuestos por línea de documento en el módulo de origen.

#### F2-04 [ALTO] Anulación incompatible con la factura electrónica — CONFIRMADO

Una factura validada por la DIAN no se anula: se emite nota crédito. Con `annul()` en ceros (F1-01), el libro y la DIAN divergen.
- [ ] Nota crédito en el módulo = asiento de reverso en Contabilidad con `source_event_type = 'CREDIT_NOTE'`.

#### F2-05 [MEDIO] `Company` EAGER en `ThreadLocal` — CONFIRMADO

`TenantContext` guarda la entidad completa. `defaultGainAccount`, `defaultLossAccount` y `city` pasaron a EAGER para esquivar `LazyInitializationException`, y `logo_data_url` es un `LONGTEXT` que se carga **en cada petición** (`entity/Company.java:116-117`).
- [ ] Guardar en el contexto un DTO inmutable (`TenantInfo`) y sacar el logo de la entidad que se carga por petición.

### 2.2 Desacoplamiento de eventos

#### F2-06 [CRÍTICO] Cero infraestructura asíncrona — CONFIRMADO

No hay `@Async`, `@Scheduled`, `Executor`, outbox ni cola. Cloud Run tiene mínimo 0 instancias y factura por petición, así que la CPU no está disponible fuera de un request. Hoy, la única forma de que facturación contabilice es **dentro** del mismo request de la factura: si el asiento falla, falla la factura, o la factura sale sin asiento.
- [ ] Tabla `posting_requests` (outbox transaccional, con `UNIQUE (company_id, source_module, source_document_id, source_event_type, source_version)`). Relé como Cloud Run Job (Cloud Scheduler) o push de Pub/Sub o Cloud Tasks. `SELECT … FOR UPDATE SKIP LOCKED` con reparto por compañía. Bandeja visible de "pendientes de contabilizar".
- [ ] Decidir por módulo: en línea o diferido.

#### F2-07 [ALTO] Reportes O(historial completo) — CONFIRMADO

`ChartOfAccountsRepository.java:189-266`: los saldos iniciales suman todo lo anterior a la fecha de corte, sin límite inferior.
- [ ] `account_period_balances` generado al cerrar el mes, más suma en vivo solo de los meses abiertos, con conciliación automática. Absorbe F1-07.

#### F2-08 [ALTO] Granularidad de los asientos automáticos — diseño

- [ ] Facturación y tesorería: un asiento por documento. Inventario perpetuo y nómina: resumidos por lote, con el detalle en el subledger.

### 2.3 Modelado de datos transversal

#### F2-09 [CRÍTICO] Sin moneda — CONFIRMADO

Las únicas apariciones de "currency" o "moneda" están en DTOs de formato de reportes. Ni `journal_entry_items` ni `t_companies` tienen moneda.
- [ ] `t_companies.functional_currency CHAR(3) DEFAULT 'COP'`; en `journal_entry_items`: `currency`, `amount_fx DECIMAL(20,4)`, `fx_rate DECIMAL(20,10)`; tabla `exchange_rates`. Hacerlo **antes** de que la tabla tenga millones de filas.

#### F2-10 [ALTO] Terceros — CONFIRMADO

`entity/ThirdParty.java`: un solo correo, teléfono y dirección (más `billingEmail`), régimen y centro de costo por defecto. No hay roles (cliente, proveedor, empleado), ni responsabilidades fiscales del RUT (O-13, O-15, O-23, O-47, R-99-PN), ni varias direcciones.
- [ ] Tablas hijas `third_party_roles`, `third_party_fiscal_responsibilities`, `third_party_addresses`. Datos de nómina en un esquema aparte, con permisos propios.

#### F2-11 [ALTO] Sin partidas abiertas — CONFIRMADO

Los renglones no tienen documento cruce ni vencimiento.
- [ ] Subledger `open_items` + `item_applications` en el módulo de CxC/CxP. El mayor solo recibe asientos.

#### F2-12 [MEDIO] Documento soporte y numeración autorizada — CONFIRMADO

- [ ] `attachments` por compañía en GCS; `numbering_ranges` (F1-04).

#### F2-13 [MEDIO] Una sola dimensión analítica — CONFIRMADO

- [ ] Agregar hoy `branch_id` y `project_id` nulos, o generalizar a `analytic_dimensions`.

---

## 3. Transversales: cumplimiento y negocio

#### T-01 [CRÍTICO] Sin bitácora, sin autoría real, sin segregación — CONFIRMADO

Une F1-02 (`AuditorAware` = 1, sin Envers en comprobantes, edición que borra), F1-27 (permisos no aplicados) y F1-01 (anular destruye). Los libros electrónicos deben garantizar integridad e inalterabilidad para tener valor probatorio (Ley 527 de 1999), y cualquier cliente con revisor fiscal pedirá el rastro. Para SOC 2 faltan los controles de cambios (CC8) y de accesos (CC6).
- [ ] Ver los checklists de F1-01, F1-02 y F1-27.
- [ ] Datos personales de terceros: Ley 1581 de 2012 (política de tratamiento y transferencia a EE. UU. ya anotadas en la arquitectura).
- [ ] PCI-DSS: dejar escrito que tesorería nunca guardará números de tarjeta (pasarela con tokenización).

#### T-02 [ALTO] No hay nivel "firma contable"

- [ ] Entidad `organizations` por encima de `t_companies`: quién paga, quién administra a los auxiliares, cómo se transfiere una compañía cuando cambia de contador.

#### T-03 [ALTO] Exportación, retención y baja por cliente

- [ ] Exportación completa por compañía en formato abierto. Conservación de 10 años (Ley 962 de 2005, art. 28): "compañía archivada, solo lectura", nunca un borrado.

#### T-04 [ALTO] Picos de vencimientos — CONFIRMADO en Terraform

`run.tf`: mínimo 0, máximo 1 instancia, 80 peticiones concurrentes, pool de 5, `db-f1-micro`.
- [ ] Prueba de carga antes de sumar clientes: 20 digitadores + 3 Libros Diarios anuales + 1 cierre de año. Medir el p95 del guardado.

---

## 4. Qué cambiar hoy, en orden

**Bloque 0: arreglos de una línea a una hora, para esta semana**
- [ ] 0.1 `JpaConfig`: `AuditorAware` desde `SecurityContextHolder` (F1-02).
- [ ] 0.2 `annul()`: `setAnnulledBy(usuario)` (F1-02).
- [ ] 0.3 `handleSave`: `if (saving) return;` (F1-03).
- [ ] 0.4 Reportes "todo-en-uno": rechazar el resultado si `content.length < totalElements` (F1-25).
- [ ] 0.5 `hibernate.default_batch_fetch_size=100` (F1-21).
- [ ] 0.6 `AuthRateLimitFilter`: dejar de confiar en el primer valor de `X-Forwarded-For` (F1-26).
- [ ] 0.7 `POST /api/auth/companies`: dejar de devolver `taxId` y `tradeName` (F1-13, paliativo).
- [ ] 0.8 `PUT /journal-entries/{id}` con la misma exigencia que anular, mientras se implementan los permisos (F1-27, paliativo).
- [ ] 0.9 Borrar `AccountingEngineService` (F1-05).

**Bloque A: integridad del libro (antes de producción multi-cliente)**
- [ ] A1. `DRAFT/POSTED/REVERSED` + storno; eliminar todo "poner en cero" (F1-01, F2-04).
- [ ] A2. `POSTED` inmutable en servicio y en base de datos; APERTURA por reverso (F1-02).
- [ ] A3. Envers en las entidades contables con `company_id` en `revinfo`, y luego `audit_log` (F1-02, T-01).
- [ ] A4. `Idempotency-Key` (F1-03).
- [ ] A5. `@Version` (F1-20).
- [ ] A6. Libro Diario y exports por streaming; selectores con búsqueda en el servidor (F1-25).
- [ ] A7. Bloqueo de la fila del período al contabilizar y al cerrar (F1-06).

**Bloque B: aislamiento y acceso (antes del segundo cliente real)**
- [ ] B1. Login en dos pasos; eliminar el directorio público (F1-13).
- [ ] B2. Permisos aplicados con `hasAuthority` + prueba que lo exija (F1-27).
- [ ] B3. Prueba de integración A/B sobre todos los endpoints en CI (F1-11).
- [ ] B4. Las 10 FKs simples que faltan, a compuestas (F1-12).
- [ ] B5. Rate limit externo y bloqueo progresivo (F1-26).
- [ ] B6. Spring Boot 3.5.x (F1-23).
- [ ] B7. `useUriResolver` restringido en openhtmltopdf (F1-17).

**Bloque C: preparación para módulos (antes de la primera línea de facturación)**
- [ ] C1. Columnas de origen + `system_kind` (F2-01, F1-08).
- [ ] C2. `LedgerPostingService` como único camino; consecutivo asignado al final (F2-02, F1-22).
- [ ] C3. Outbox + relé + bandeja (F2-06, F1-16).
- [ ] C4. Moneda (F2-09).
- [ ] C5. Impuestos con vigencias y código DIAN (F2-03).
- [ ] C6. `account_period_balances` (F2-07, F1-07).
- [ ] C7. Tablas hijas de terceros (F2-10).

---

## 5. Las 5 preguntas que todavía no respondes

1. **El revisor fiscal pide el CE-120 de marzo tal como estaba cuando se declaró el IVA, y en junio alguien lo editó o lo anuló. ¿Qué le muestras?** Con el código de hoy la respuesta es "nada": `update()` borra los renglones, `annul()` los pone en cero, `JournalEntry` no tiene Envers y `created_by` dice 1 en todos los registros.

2. **¿Cuántos clientes tuyos tendrán más de 2.000 comprobantes en un año o más de 1.000 terceros, y cuántos Libros Diarios incompletos se habrán impreso ya en tus pruebas sin que nadie lo notara?** El truncamiento no avisa, y los totales del libro truncado cuadran consigo mismos.

3. **Un auxiliar de la compañía A reemplaza mal el comprobante de apertura a las 3 p. m. y el cliente te pide volver a las 2:59. ¿Cómo lo haces sin devolver en el tiempo a todos los demás clientes que siguieron trabajando?** Si el procedimiento no está escrito ni probado en el `restore_drill`, la respuesta real es "no se puede".

4. **La factura electrónica sale validada por la DIAN y el asiento falla (marzo cerrado, cuenta inactiva, centro de costo faltante). ¿Dónde queda ese asiento pendiente, quién se entera, quién lo reintenta y qué impide que el reintento lo contabilice dos veces?** Hoy no existe ni el lugar donde podría quedar pendiente: ni outbox, ni job, ni columna de origen.

5. **Un `ACCOUNTANT` no puede anular, pero puede abrir el mismo comprobante y cambiarle todas las cuentas y montos, y el sistema registra que lo hizo el usuario 1. ¿Cuál es entonces, en concreto, el control que impide que un auxiliar descontento altere la contabilidad de un mes ya revisado pero no cerrado?**
