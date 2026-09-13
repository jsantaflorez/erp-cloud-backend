# Nombres truncados detectados en las plantillas PUC

Generado 2026-09-10, junto con `2026-09-10_chart_account_templates.sql`.
Estos nombres se cargan tal cual (el usuario pidio avanzar ahora y corregir
despues). Corregirlos mas adelante es solo un UPDATE sobre
`chart_account_templates`, no requiere ningun cambio de codigo.

## Comercial.csv
No se encontraron truncamientos. Los nombres mas largos (99 caracteres) son
oraciones completas, no cortadas a mitad de palabra.

## Solidario.csv

### Truncamiento corto (aparente limite de columna de origen ~5-6 caracteres)
| Codigo | Nombre actual | Probable nombre completo |
|---|---|---|
| 120115 | COMPA | COMPAÑIAS DE FINANCIAMIENTO... (segun contexto) |
| 120220 | COMPA | idem |
| 220515 | COMPA | idem |
| 221020 | COMPA | idem |
| 139015 | POR P | POR PAGAR... |
| 4160 | ENSE | ENSERES |
| 6160 | ENSE | ENSERES |
| 425520 | DA | (fragmento, sin contexto suficiente) |
| 514051 | P | (fragmento, sin contexto suficiente) |
| 514053 | P | (fragmento, sin contexto suficiente) |
| 830505 | PA | (fragmento, sin contexto suficiente) |
| 912505 | CR | (fragmento, sin contexto suficiente) |
| 914010 | EN PR | EN PROCESO... |
| 914015 | EN DEP | EN DEPOSITO... |

### Truncamiento largo (columna de origen cortada exactamente a 100 caracteres)
| Codigo | Nombre actual (cortado a 100 car.) |
|---|---|
| 120305 | TITULOS EMITIDOS, AVALADOS, ACEPTADOS O GARANTIZADOS POR INSTITUCIONES VIGILADAS POR LA SUPERINTENDE |
| 120411 | TITULOS EMITIDOS, AVALADOS, ACEPTADOS O GARANTIZADOS POR INSTITUCIONES VIGILADAS POR LA SUPERINTENDE |
| 120414 | TITULOS EMITIDOS POR ENTIDADES NO VIGILADAS POR LA SUPERINTENDENCIA BANCARIA (INCLUIDOS LOS BONOS OB |
| 120610 | PARTICIPACIONES EN FONDOS MUTUOS DE INVERSION INTERNACIONALES QUE INVIERTAN EXCLUSIVAMENTE EN TITULO |
| 120612 | PARTICIPACION EN FONDOS MUTUOS DE INVERSION INTERNACIONALES QUE INVIERTAN EXCLUSIVAMENTE EN RENTA VA |
| 120811 | TITULOS EMITIDOS, AVALADOS, ACEPTADOS O GARANTIZADOS POR INSTITUCIONES VIGILADAS POR LA SUPERINTENDE |
| 120814 | TITULOS EMITIDOS POR ENTIDADES NO VIGILADAS POR LA SUPERINTENDENCIA BANCARIA (INCLUIDOS LOS BONOS OB |
| 121311 | TITULOS EMITIDOS, AVALADOS, ACEPTADOS O GARANTIZADOS POR INSTITUCIONES VIGILADAS POR LA SUPERINTENDE |
| 121314 | TITULOS EMITIDOS POR ENTIDADES NO VIGILADAS POR LA SUPERINTENDENCIA BANCARIA (INCLUIDOS LOS BONOS OB |
| 123111 | TITULOS EMITIDOS, AVALADOS, ACEPTADOS O GARANTIZADOS POR INSTITUCIONES VIGILADAS POR LA SUPERINTENDE |
| 123511 | TITULOS EMITIDOS, AVALADOS, ACEPTADOS O GARANTIZADOS POR INSTITUCIONES VIGILADAS POR LA SUPERINTENDE |
| 123514 | TITULOS EMITIDOS POR ENTIDADES NO VIGILADAS POR LA SUPERINTENDENCIA BANCARIA (INCLUIDOS LOS BONOS OB |

Total: 14 filas con truncamiento corto + 12 filas con truncamiento largo = 26 filas de ~5,550 (0.5%).

## Como corregirlos despues
```sql
UPDATE chart_account_templates
SET name = 'NOMBRE COMPLETO AQUI'
WHERE template_type = 'SOLIDARIO' AND code = '120115';
```
No requiere reiniciar la aplicacion ni tocar codigo Java: la tabla es de solo lectura para la app pero editable libremente por SQL.
