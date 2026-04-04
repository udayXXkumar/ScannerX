import api from './axios';

export const getDashboardSummary = async ({ targetId, scanId } = {}) => {
  const params = new URLSearchParams();
  if (targetId) {
    params.set('targetId', targetId);
  }
  if (scanId) {
    params.set('scanId', scanId);
  }
  const { data } = await api.get(`/dashboard/summary${params.toString() ? `?${params.toString()}` : ''}`);
  return data;
};
