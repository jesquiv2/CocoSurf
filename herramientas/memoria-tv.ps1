<#
.SYNOPSIS
  Registra, desde el PC, quién consume la memoria del televisor (TODAS las apps) mientras lo usas.

.DESCRIPTION
  Android no deja a una app medir a las demás, pero el PC sí puede con adb. Este script pregunta
  cada pocos segundos por `dumpsys meminfo` y por /proc/meminfo, guarda todo en un CSV y avisa
  en pantalla cuando un proceso se dispara. Al terminar (Ctrl+C o al cumplirse el tiempo) muestra
  quién fue el que más memoria usó y quién más creció.

.EXAMPLE
  .\memoria-tv.ps1 -Dispositivo 192.168.1.50:5555 -Intervalo 5 -Minutos 30

  1. Conecta el televisor:  adb connect 192.168.1.50:5555
  2. Ejecuta el script y usa el televisor con normalidad (abre YouTube, Facebook…).
  3. Al terminar, abre el CSV (o mándamelo) para ver en qué momento se disparó cada proceso.
#>
param(
    [string]$Adb = "adb",
    [string]$Dispositivo = "",
    [int]$Intervalo = 5,
    [int]$Minutos = 30,
    [int]$AvisoMb = 60,
    [string]$Salida = ""
)

$ErrorActionPreference = "Stop"
$argsAdb = @()
if ($Dispositivo -ne "") { $argsAdb = @("-s", $Dispositivo) }

function Invoke-Adb([string[]]$Cmd) {
    $r = & $Adb @argsAdb @Cmd 2>&1
    return ($r | Out-String)
}

$estado = Invoke-Adb @("get-state")
if ($estado -notmatch "device") {
    Write-Host "No encuentro el televisor ($($estado.Trim())). Conéctalo con: adb connect IP:5555" -ForegroundColor Red
    exit 1
}

if ($Salida -eq "") { $Salida = "memoria-tv-$(Get-Date -Format 'yyyyMMdd-HHmmss').csv" }
"tiempo,tipo,nombre,pid,mb" | Out-File -FilePath $Salida -Encoding utf8
Write-Host "Registrando cada $Intervalo s durante $Minutos min en: $Salida   (Ctrl+C para terminar antes)" -ForegroundColor Cyan

$previo = @{}      # nombre -> MB de la muestra anterior
$maximo = @{}      # nombre -> MB máximo
$primero = @{}     # nombre -> primer MB visto
$fin = (Get-Date).AddMinutes($Minutos)
$reProc = [regex]'^\s*([\d.,]+)K:\s+(.+?)\s+\(pid\s+(\d+)'

function ToMb([string]$k) { return [math]::Round(([double]($k -replace '[.,]', '')) / 1024, 0) }

try {
    while ((Get-Date) -lt $fin) {
        $t = Get-Date -Format "HH:mm:ss"
        $texto = Invoke-Adb @("shell", "dumpsys", "meminfo")
        $enSeccion = $false
        $actual = @{}
        foreach ($linea in ($texto -split "`r?`n")) {
            if ($linea.StartsWith("Total PSS by process")) { $enSeccion = $true; continue }
            if ($enSeccion -and $linea.Trim() -eq "") { if ($actual.Count -gt 0) { break } else { continue } }
            if (-not $enSeccion) { continue }
            $m = $reProc.Match($linea)
            if ($m.Success) {
                $nombre = $m.Groups[2].Value
                $mb = ToMb $m.Groups[1].Value
                $actual[$nombre] = $mb
                "$t,proceso,$nombre,$($m.Groups[3].Value),$mb" | Out-File -FilePath $Salida -Append -Encoding utf8
            }
        }
        foreach ($etq in @("Total RAM", "Free RAM", "Used RAM", "Lost RAM", "ZRAM")) {
            $m = [regex]::Match($texto, "$etq`:\s*([\d.,]+)K")
            if ($m.Success) { "$t,ram,$etq,,$(ToMb $m.Groups[1].Value)" | Out-File -FilePath $Salida -Append -Encoding utf8 }
        }
        $mi = Invoke-Adb @("shell", "cat", "/proc/meminfo")
        $campos = @{}
        foreach ($l in ($mi -split "`r?`n")) { if ($l -match '^(\w+):\s+(\d+)') { $campos[$Matches[1]] = [double]$Matches[2] / 1024 } }
        if ($campos.ContainsKey("MemTotal")) {
            $libre = [math]::Round($campos["MemFree"])
            $cache = [math]::Round($campos["Cached"] + $campos["Buffers"])
            $anon = [math]::Round($campos["AnonPages"])
            $kern = [math]::Round($campos["Slab"] + $campos["KernelStack"] + $campos["PageTables"])
            $drv = [math]::Round($campos["MemTotal"]) - $libre - $cache - $anon - $kern
            foreach ($par in @(@("Libre", $libre), @("Cache", $cache), @("Apps", $anon), @("Kernel", $kern), @("Controladores", $drv))) {
                "$t,reparto,$($par[0]),,$($par[1])" | Out-File -FilePath $Salida -Append -Encoding utf8
            }
            Write-Host ("[{0}] libre {1} MB · caché {2} · apps {3} · kernel {4} · controladores {5}" -f $t, $libre, $cache, $anon, $kern, $drv)
        }
        foreach ($n in $actual.Keys) {
            if (-not $primero.ContainsKey($n)) { $primero[$n] = $actual[$n] }
            if (-not $maximo.ContainsKey($n) -or $actual[$n] -gt $maximo[$n]) { $maximo[$n] = $actual[$n] }
            if ($previo.ContainsKey($n) -and ($actual[$n] - $previo[$n]) -ge $AvisoMb) {
                Write-Host ("   ⚠ [{0}] {1} subió {2} MB de golpe (ahora {3} MB)" -f $t, $n, ($actual[$n] - $previo[$n]), $actual[$n]) -ForegroundColor Yellow
            }
        }
        $previo = $actual
        Start-Sleep -Seconds $Intervalo
    }
}
finally {
    Write-Host "`n── Resumen ──" -ForegroundColor Cyan
    Write-Host "Los que más memoria llegaron a usar (MB):"
    $maximo.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 10 |
        ForEach-Object { "{0,6}  {1}" -f $_.Value, $_.Key }
    Write-Host "`nLos que más crecieron desde que empezó el registro (MB):"
    $maximo.GetEnumerator() |
        ForEach-Object { [pscustomobject]@{ Nombre = $_.Key; Crecio = $_.Value - $primero[$_.Key] } } |
        Sort-Object Crecio -Descending | Select-Object -First 8 |
        ForEach-Object { "{0,6}  {1}" -f $_.Crecio, $_.Nombre }
    Write-Host "`nDatos completos en: $Salida"
}
