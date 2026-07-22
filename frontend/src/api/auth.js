import { http } from './http'

const responseData = (response) => response.data?.data ?? response.data ?? {}

export async function fetchUserSession() {
  const response = await http.get('/v1/auth/session', { skipAuthNotification: true })
  return responseData(response)
}

export async function registerUser(email, password) {
  const response = await http.post('/v1/auth/register', { email, password })
  return responseData(response)
}

export async function loginUser(email, password) {
  const response = await http.post('/v1/auth/login', { email, password })
  return responseData(response)
}

export async function logoutUser() {
  const response = await http.post('/v1/auth/logout')
  return responseData(response)
}
