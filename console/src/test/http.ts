/** A JSON response shaped like the backend's `ApiResponse` envelope. */
export function apiResponse(
  status: number,
  body: { code: string; message?: string; data?: unknown },
) {
  return new Response(JSON.stringify({ message: '', data: null, ...body }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

export function ok(data: unknown = null) {
  return apiResponse(200, { code: 'OK', message: 'success', data })
}
