import api from './axios';

export const login = async (credentials) => {
  const { data } = await api.post('/auth/login', credentials);
  return data;
};

export const register = async (userData) => {
  const { data } = await api.post('/auth/register', userData);
  return data;
};

export const getCurrentUser = async () => {
  const { data } = await api.get('/auth/me');
  return data;
};

export const updateProfile = async (payload) => {
  const { data } = await api.put('/auth/profile', payload);
  return data;
};

export const changePassword = async (payload) => {
  const { data } = await api.post('/auth/password', payload);
  return data;
};

export const deleteCurrentUser = async () => {
  const { data } = await api.delete('/auth/me');
  return data;
};
