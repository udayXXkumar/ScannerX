import api from './axios';

function buildQuery(params = {}) {
  const query = new URLSearchParams();
  if (params.targetId) {
    query.set('targetId', params.targetId);
  }
  if (params.scanId) {
    query.set('scanId', params.scanId);
  }
  return query.toString();
}

export const getReportSummary = async (params = {}) => {
  const query = buildQuery(params);
  const { data } = await api.get(`/reports/summary${query ? `?${query}` : ''}`);
  return data;
};

const triggerDownload = (blobData, fileName) => {
  const url = window.URL.createObjectURL(new Blob([blobData]));
  const link = document.createElement('a');
  link.href = url;
  link.setAttribute('download', fileName);
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(url);
};

export const downloadReportCsv = async (params = {}) => {
  const query = buildQuery(params);
  const response = await api.get(`/reports/export/csv${query ? `?${query}` : ''}`, { responseType: 'blob' });
  triggerDownload(response.data, 'scannerx-report.csv');
};

export const downloadReportPdf = async (params = {}) => {
  const query = buildQuery(params);
  const response = await api.get(`/reports/export/pdf${query ? `?${query}` : ''}`, { responseType: 'blob' });
  triggerDownload(response.data, 'scannerx-report.pdf');
};

export const downloadReportJson = async (params = {}) => {
  const query = buildQuery(params);
  const { data } = await api.get(`/reports/export/json${query ? `?${query}` : ''}`);
  triggerDownload(JSON.stringify(data, null, 2), 'scannerx-report.json');
};
