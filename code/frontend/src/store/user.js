import { defineStore } from 'pinia'
import { login, logout, getMe } from '@/api/auth'

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || sessionStorage.getItem('token') || '',
    userInfo: JSON.parse(localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo') || 'null')
  }),
  getters: {
    isAuthenticated: (state) => !!state.token,
    isAdmin: (state) => state.userInfo?.role === 'ADMIN',
    userId: (state) => state.userInfo?.userId,
    username: (state) => state.userInfo?.username,
    name: (state) => state.userInfo?.name,
    deptName: (state) => state.userInfo?.deptName
  },
  actions: {
    async handleLogin(credentials, remember = true) {
      const data = await login(credentials)
      this.token = data.token
      this.userInfo = data.user

      const storage = remember ? localStorage : sessionStorage
      storage.setItem('token', data.token)
      storage.setItem('userInfo', JSON.stringify(data.user))
      return data
    },
    async fetchUserInfo() {
      if (!this.token) return null
      try {
        const user = await getMe()
        this.userInfo = user
        localStorage.setItem('userInfo', JSON.stringify(user))
        return user
      } catch (err) {
        this.handleLogout()
        return null
      }
    },
    async handleLogout() {
      try {
        await logout()
      } catch (e) {
        // ignore
      } finally {
        this.token = ''
        this.userInfo = null
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        sessionStorage.removeItem('token')
        sessionStorage.removeItem('userInfo')
      }
    }
  }
})
