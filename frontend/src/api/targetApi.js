import api from './axios';

export const getTargets = async () => {
  const { data } = await api.get('/targets');
  return data;
};

export const createTarget = async (target) => {
  const { data } = await api.post('/targets', target);
  return data;
};

export const updateTarget = async (id, target) => {
  const { data } = await api.put(`/targets/${id}`, target);
  return data;
};

export const deleteTarget = async (id) => {
  const { data } = await api.post(`/targets/${id}/delete`);
  return data;
};
