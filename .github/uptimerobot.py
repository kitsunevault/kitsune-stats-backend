#!/usr/bin/env python3
"""Pausa y reanuda en UptimeRobot los monitores de los servicios que reinicia el despliegue,
para que un despliegue controlado no mande llamadas ni mensajes.

  uptimerobot.py pausar ARCHIVO /orders-api/ /cart-api/ ...
      Pausa los monitores encendidos cuyo URL tenga alguno de esos pedazos y apunta
      sus ids en ARCHIVO (uno por renglón, conforme los pausa).
  uptimerobot.py reanudar ARCHIVO
      Vuelve a encender los que quedaron apuntados en ARCHIVO.

La llave (Main API Key de UptimeRobot) viene del secreto UPTIMEROBOT_API_KEY.
Sin llave no hace nada. Nunca imprime la llave ni lo que UptimeRobot devuelve tal cual.
"""
import json
import os
import sys
import urllib.parse
import urllib.request

API = "https://api.uptimerobot.com/v2/"
PAUSADO = 0
ENCENDIDO = 1


def llamar(metodo, **datos):
    datos.update(api_key=os.environ["UPTIMEROBOT_API_KEY"], format="json")
    pedido = urllib.request.Request(
        API + metodo,
        data=urllib.parse.urlencode(datos).encode(),
        headers={"Content-Type": "application/x-www-form-urlencoded", "Cache-Control": "no-cache"},
        method="POST",
    )
    with urllib.request.urlopen(pedido, timeout=20) as respuesta:
        cuerpo = json.load(respuesta)
    if cuerpo.get("stat") != "ok":
        error = cuerpo.get("error") or {}
        # Solo tipo y mensaje: el error de UptimeRobot puede traer la llave en passed_value.
        raise SystemExit(f"UptimeRobot {metodo} falló: {error.get('type')} {error.get('message')}")
    return cuerpo


def monitores():
    offset = 0
    while True:
        pagina = llamar("getMonitors", offset=offset, limit=50)
        yield from pagina.get("monitors", [])
        offset += 50
        if offset >= pagina.get("pagination", {}).get("total", 0):
            return


def pausar(archivo, pedazos):
    pausados = 0
    with open(archivo, "a") as apuntados:
        for m in monitores():
            if m.get("status") == PAUSADO or not any(p in (m.get("url") or "") for p in pedazos):
                continue
            llamar("editMonitor", id=m["id"], status=PAUSADO)
            apuntados.write(f"{m['id']}\n")
            apuntados.flush()
            pausados += 1
            print(f"Pausado: {m.get('friendly_name')}")
    print(f"{pausados} monitores pausados mientras se reinician los servicios.")


def reanudar(archivo):
    if not os.path.exists(archivo):
        print("No se pausó ningún monitor.")
        return
    with open(archivo) as apuntados:
        ids = [renglon.strip() for renglon in apuntados if renglon.strip()]
    for monitor_id in ids:
        llamar("editMonitor", id=monitor_id, status=ENCENDIDO)
    print(f"{len(ids)} monitores encendidos de nuevo.")


def main():
    if not os.environ.get("UPTIMEROBOT_API_KEY"):
        print("Sin el secreto UPTIMEROBOT_API_KEY: los monitores no se tocan.")
        return
    accion, archivo, *pedazos = sys.argv[1:]
    if accion == "pausar":
        pausar(archivo, pedazos)
    elif accion == "reanudar":
        reanudar(archivo)
    else:
        raise SystemExit(f"Acción desconocida: {accion}")


if __name__ == "__main__":
    main()
