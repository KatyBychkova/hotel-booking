$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$http = New-Object System.Net.Http.HttpClient
$http.BaseAddress = [uri]'http://localhost:8080'
$http.Timeout = [TimeSpan]::FromSeconds(15)

function Send-Request($method, $path, $body, $token) {
    $req = New-Object System.Net.Http.HttpRequestMessage
    $req.Method = New-Object System.Net.Http.HttpMethod($method)
    $req.RequestUri = [uri]::new($path, [UriKind]::Relative)
    if ($token) {
        $req.Headers.Authorization = New-Object System.Net.Http.Headers.AuthenticationHeaderValue('Bearer', $token)
    }
    if ($null -ne $body) {
        $req.Content = New-Object System.Net.Http.StringContent(($body | ConvertTo-Json), [Text.Encoding]::UTF8, 'application/json')
    }
    return $http.SendAsync($req)
}

function Read-Result($task, $expected) {
    $response = $task.GetAwaiter().GetResult()
    try {
        $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if ([int]$response.StatusCode -notin $expected) {
            throw "Expected HTTP $expected, received $([int]$response.StatusCode): $body"
        }
        if ($body) { return ($body | ConvertFrom-Json) }
    } finally {
        $response.Dispose()
    }
}

function Check($condition, $message) {
    if (!$condition) { throw $message }
}

try {
    # Wait until both services can be reached through Eureka and the gateway.
    $admin = $null
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        try {
            $login = @{username='admin'; password=$env:ADMIN_PASSWORD}
            $admin = (Read-Result (Send-Request POST '/api/bookings/user/auth' $login $null) 200).token
            $hotels = @(Read-Result (Send-Request GET '/api/hotels' $null $admin) 200)
            if ($hotels.Count -ge 2) { break }
        } catch {
            if ($attempt -eq 89) { throw }
        }
        Start-Sleep -Seconds 2
    }

    $login = @{username=('guest' + [guid]::NewGuid().ToString('N')); password='demo-password'}
    $token = (Read-Result (Send-Request POST '/api/bookings/user/register' $login $null) 201).token
    $start = (Get-Date).AddDays(10).ToString('yyyy-MM-dd')
    $end = (Get-Date).AddDays(12).ToString('yyyy-MM-dd')
    $query = "?hotelId=1&startDate=$start&endDate=$end"
    $rooms = @(Read-Result (Send-Request GET ("/api/bookings/recommend" + $query) $null $token) 200)
    Check ($rooms.Count -eq 2) 'Expected two operational rooms in the demo hotel.'

    $a = @{requestId=[guid]::NewGuid(); autoSelect=$true; roomId=999; hotelId=1; startDate=$start; endDate=$end}
    $b = $a.Clone()
    $b.requestId = [guid]::NewGuid()
    $first = Send-Request POST '/api/bookings' $a $token
    $second = Send-Request POST '/api/bookings' $b $token
    $one = Read-Result $first 201
    $two = Read-Result $second 201
    Check ($one.status -eq 'CONFIRMED' -and $two.status -eq 'CONFIRMED' -and $one.roomId -ne $two.roomId) 'Parallel allocation failed.'

    $repeat = Read-Result (Send-Request POST '/api/bookings' $a $token) 200
    Check ($repeat.id -eq $one.id) 'Repeated request created another booking.'
    $manual = @{requestId=[guid]::NewGuid(); autoSelect=$false; roomId=$one.roomId; startDate=$start; endDate=$end}
    $null = Read-Result (Send-Request POST '/api/bookings' $manual $token) 409
    $history = Read-Result (Send-Request GET '/api/bookings?size=1' $null $token) 200
    Check ($history.total -eq 3 -and $history.items.Count -eq 1) 'Booking history or pagination is wrong.'

    $stats = @(Read-Result (Send-Request GET ("/api/rooms/stats" + $query) $null $admin) 200)
    Check (@($stats | Where-Object occupiedNights -eq 2).Count -eq 2) 'Occupied nights are wrong.'
    $null = Read-Result (Send-Request GET ("/api/rooms/stats" + $query) $null $token) 403
    $null = Read-Result (Send-Request POST "/api/rooms/1/release" @{} $token) 403
    $null = Read-Result (Send-Request GET '/api/bookings' $null $null) 401

    $cancelled = Read-Result (Send-Request DELETE ("/api/bookings/" + $one.id) $null $token) 200
    Check ($cancelled.status -eq 'CANCELLED') 'Cancellation failed.'
    $free = @(Read-Result (Send-Request GET ("/api/rooms/recommend" + $query) $null $token) 200)
    Check ($free.Count -eq 1 -and $free[0].id -eq $one.roomId -and $free[0].timesBooked -eq 0) 'Room was not released.'

    # Both rooms are free on each of these dates. The less frequently used room should be first.
    $counts = @{}
    $counts[[long]$one.roomId] = 0
    $counts[[long]$two.roomId] = 1
    for ($i = 0; $i -lt 7; $i++) {
        $a.requestId = [guid]::NewGuid()
        $a.startDate = (Get-Date).AddDays(20 + $i).ToString('yyyy-MM-dd')
        $a.endDate = (Get-Date).AddDays(21 + $i).ToString('yyyy-MM-dd')
        $expected = $counts.Keys | Sort-Object @{Expression = { $counts[$_] }}, @{Expression = { $_ }} | Select-Object -First 1
        $next = Read-Result (Send-Request POST '/api/bookings' $a $token) 201
        Check ($next.roomId -eq $expected) 'Automatic selection ignored booking counts.'
        $counts[[long]$next.roomId]++
    }
    Check ($counts[[long]$one.roomId] -eq $counts[[long]$two.roomId]) 'Room load is uneven.'

    foreach ($port in @(8081, 8082)) {
        $spec = Invoke-RestMethod "http://localhost:$port/v3/api-docs"
        Check ($spec.openapi -and @($spec.paths.PSObject.Properties).Count -gt 0) 'OpenAPI specification is missing.'
        $ui = Invoke-WebRequest "http://localhost:$port/swagger-ui/index.html" -UseBasicParsing
        Check ($ui.StatusCode -eq 200) 'Swagger UI is unavailable.'
    }
    Write-Host 'PASS: Eureka, routing, JWT, concurrent allocation, idempotency, conflict, history, stats, release, balanced allocation and Swagger.'
} finally {
    $http.Dispose()
}
