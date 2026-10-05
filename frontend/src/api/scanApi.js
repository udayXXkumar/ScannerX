import api from './axios';

export const getScans = async () => {
  const { data } = await api.get('/scans');
  return data;
};

export const getScanById = async (id) => {
  const { data } = await api.get(`/scans/${id}`);
  return data;
};

export const getScanReportJson = async (scanId) => {
  const { data } = await api.get(`/reports/scans/${scanId}/json`);
  return data;
};

export const createScan = async (scan) => {
  const payload = {
    ...scan,
    name: scan?.name || scan?.target?.name || '',
  };
  const { data } = await api.post('/scans', payload);
  return data;
};

export const deleteScan = async (id) => {
  const { data } = await api.delete(`/scans/${id}`);
  return data;
};

export const cancelScan = async (id) => {
  const { data } = await api.post(`/scans/${id}/cancel`);
  return data;
};

export const pauseScan = async (id) => {
  const { data } = await api.post(`/scans/${id}/pause`);
  return data;
};

export const resumeScan = async (id) => {
  const { data } = await api.post(`/scans/${id}/resume`);
  return data;
};

export const getScanActivity = async (id) => {
  const { data } = await api.get(`/scans/${id}/activity`);
  return data;
};

export const downloadScanReportCsv = async (scanId) => {
  const response = await api.get(`/reports/scans/${scanId}/csv`, { responseType: 'blob' });
  const url = window.URL.createObjectURL(new Blob([response.data]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', `scan_${scanId}_report.csv`);
  document.body.appendChild(link);
  link.click();
  link.remove();
};

export const downloadScanReportJson = async (scanId) => {
  const { data } = await api.get(`/reports/scans/${scanId}/json`);
  const url = window.URL.createObjectURL(new Blob([JSON.stringify(data, null, 2)]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', `scan_${scanId}_report.json`);
  document.body.appendChild(link);
  link.click();
  link.remove();
};
