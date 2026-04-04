import api from './axios';

export const getNotifications = async () => {
  const { data } = await api.get('/notifications');
  return data;
};

export const getUnreadNotificationCount = async () => {
  const { data } = await api.get('/notifications/unread/count');
  return data;
};

export const markNotificationRead = async (id) => {
  const { data } = await api.post(`/notifications/${id}/read`);
  return data;
};

export const deleteNotification = async (id) => {
  const { data } = await api.delete(`/notifications/${id}`);
  return data;
};
