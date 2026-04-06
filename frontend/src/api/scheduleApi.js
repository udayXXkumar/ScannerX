import api from './axios';

export const getSchedules = async () => {
  const { data } = await api.get('/schedules');
  return data;
};

export const createSchedule = async (scheduleData) => {
  const { data } = await api.post('/schedules', scheduleData);
  return data;
};

export const cancelSchedule = async (id) => {
  const { data } = await api.post(`/schedules/${id}/cancel`);
  return data;
};
