import api from './axios';

export const getFindings = async ({ targetId, scanId, completedOnly = true } = {}) => {
  const params = new URLSearchParams();
  if (targetId) {
    params.set('targetId', targetId);
  }
  if (scanId) {
    params.set('scanId', scanId);
  }
  params.set('completedOnly', completedOnly ? 'true' : 'false');
  const { data } = await api.get(`/findings?${params.toString()}`);
  return data;
};

export const getFindingById = async (id) => {
  const { data } = await api.get(`/findings/${id}`);
  return data;
};

export const updateFinding = async (id, findingData) => {
  const { data } = await api.put(`/findings/${id}`, findingData);
  return data;
};
