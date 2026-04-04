import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { register } from '../../api/authApi';
import TermsConsentField from '../../components/auth/TermsConsentField';

const Register = () => {
  const [formData, setFormData] = useState({ fullName: '', email: '', password: '' });
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [error, setError] = useState(null);
  const [termsError, setTermsError] = useState(null);
  const [success, setSuccess] = useState(false);
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!acceptedTerms) {
      setTermsError('Please accept the Terms & Conditions before creating an account.');
      return;
    }
    setTermsError(null);
    setLoading(true);
    try {
      await register(formData);
      setSuccess(true);
      setTimeout(() => navigate('/login'), 2000);
    } catch (err) {
      setError(err.response?.data || 'An error occurred during registration.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full">
      <div className="auth-heading">
        <p className="auth-eyebrow">Sign up</p>
        <h1 className="auth-title">Sign up</h1>
      </div>

      {error ? (
        <div className="mb-4 rounded-2xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
          {error}
        </div>
      ) : null}

      {success ? (
        <div className="mb-4 rounded-2xl border border-emerald-400/20 bg-emerald-400/10 px-4 py-3 text-sm text-emerald-200">
          Registration successful. Redirecting to login...
        </div>
      ) : null}

      <form onSubmit={handleSubmit} className="form-stack">
        <div className="form-field">
          <label className="block text-[11px] text-zinc-500">
            Name<span className="text-rose-300">*</span>
          </label>
          <input
            type="text"
            required
            value={formData.fullName}
            onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
            className="app-input"
            placeholder="Enter your name"
          />
        </div>

        <div className="form-field">
          <label className="block text-[11px] text-zinc-500">
            Email<span className="text-rose-300">*</span>
          </label>
          <input
            type="email"
            required
            value={formData.email}
            onChange={(e) => setFormData({ ...formData, email: e.target.value })}
            className="app-input"
            placeholder="Enter your email"
          />
        </div>

        <div className="form-field">
          <label className="block text-[11px] text-zinc-500">
            Password<span className="text-rose-300">*</span>
          </label>
          <input
            type="password"
            required
            minLength={8}
            value={formData.password}
            onChange={(e) => setFormData({ ...formData, password: e.target.value })}
            className="app-input"
            placeholder="Password"
          />
        </div>

        <TermsConsentField
          id="register-terms"
          checked={acceptedTerms}
          error={termsError}
          onChange={(event) => {
            setAcceptedTerms(event.target.checked);
            if (event.target.checked) {
              setTermsError(null);
            }
          }}
        />

        <button
          type="submit"
          disabled={loading || success || !acceptedTerms}
          className="surface-button-primary w-full"
        >
          {loading ? 'Registering...' : 'Sign up'}
        </button>
      </form>

      <div className="auth-footer">
        Already have an account?{' '}
        <Link to="/login" className="app-link font-medium">
          Log in
        </Link>
      </div>
    </div>
  );
};

export default Register;
