# Pruebas EF-02 a EF-09 (solo casos que el servidor RECHAZA: no guardan datos ni disparan alertas)
$U = "https://tzjziqjrxgquzkjbldml.supabase.co/functions/v1/recibir-lectura"
$tok = Read-Host "Pega el token del nodo (TOKEN_NODO del programa del ESP32)"

function Cuerpo($dev, $seq, $dist, $edad) {
  return ('{"dev_eui":"' + $dev + '","seq":' + $seq + ',"distancia_mm":' + $dist + ',"edad_min":' + $edad + '}')
}
function Probar($id, $esperado, $metodo, $headers, $body) {
  $p = @{ Uri = $U; Method = $metodo; Headers = $headers; UseBasicParsing = $true; ErrorAction = "Stop" }
  if ($metodo -ne "GET") { $p.Body = $body; $p.ContentType = "application/json" }
  try { $r = Invoke-WebRequest @p; $code = [int]$r.StatusCode; $txt = $r.Content }
  catch {
    $code = [int]$_.Exception.Response.StatusCode
    $sr = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $txt = $sr.ReadToEnd()
  }
  $ok = if ($code -eq $esperado) { "APROBADA" } else { "FALLO" }
  "$id | esperado $esperado | obtenido $code | $ok | $txt"
}

$h  = @{ "x-token" = $tok }
$sin = @{}
$mal = @{ "x-token" = "token-incorrecto" }
$buen = Cuerpo "nodo-torre-01" 1 1100 0

$salida = @()
$salida += Probar "EF-02 sin x-token"            401 "POST" $sin $buen
$salida += Probar "EF-03 x-token incorrecto"     401 "POST" $mal $buen
$salida += Probar "EF-04 metodo GET"             405 "GET"  $h   $null
$salida += Probar "EF-05a cuerpo no es JSON"     400 "POST" $h   "esto no es json"
$salida += Probar "EF-05b faltan campos"         400 "POST" $h   '{"dev_eui":"nodo-torre-01"}'
$salida += Probar "EF-06 nodo desconocido"       403 "POST" $h   (Cuerpo "nodo-inexistente" 1 1100 0)
$salida += Probar "EF-07a distancia 19"          400 "POST" $h   (Cuerpo "nodo-torre-01" 1 19 0)
$salida += Probar "EF-07b distancia 4001"        400 "POST" $h   (Cuerpo "nodo-torre-01" 1 4001 0)
$salida += Probar "EF-08a seq -1"                400 "POST" $h   (Cuerpo "nodo-torre-01" -1 1100 0)
$salida += Probar "EF-08b seq 65536"             400 "POST" $h   (Cuerpo "nodo-torre-01" 65536 1100 0)
$salida += Probar "EF-09a edad_min -1"           400 "POST" $h   (Cuerpo "nodo-torre-01" 1 1100 -1)
$salida += Probar "EF-09b edad_min 10081"        400 "POST" $h   (Cuerpo "nodo-torre-01" 1 1100 10081)

$salida | ForEach-Object { Write-Host $_ }
$salida | Out-File -Encoding utf8 "$PSScriptRoot\resultados_api.txt"
Write-Host ""
Write-Host "Listo. Resultados guardados en resultados_api.txt (copialos y pegalos en el chat)."
