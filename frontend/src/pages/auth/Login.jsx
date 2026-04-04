import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { login } from '../../api/authApi';
import TermsConsentField from '../../components/auth/TermsConsentField';

const Login = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [acceptedTerms, setAcceptedTerms] = useState(false);
  const [error, setError] = useState(null);
  const [termsError, setTermsError] = useState(null);
  const [loading, setLoading] = useState(false);
  const { loginUser } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    if (!acceptedTerms) {
      setTermsError('Please accept the Terms & Conditions before signing in.');
      return;
    }
    setTermsError(null);
    setLoading(true);
    try {
      const data = await login({ email, password });
      loginUser(data);
      navigate('/dashboard');
    } catch (err) {
      const errorMessage = err.response?.data?.message 
        || err.message 
        || 'Invalid email or password';
      setError(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="w-full">
      <div className="auth-heading">
        <p className="auth-eyebrow">Sign in</p>
        <h1 className="auth-title">Sign in</h1>
      </div>

      {error ? (
        <div className="mb-4 rounded-2xl border border-rose-500/20 bg-rose-500/10 px-4 py-3 text-sm text-rose-200">
          {error}
        </div>
      ) : null}

      <form onSubmit={handleSubmit} className="form-stack">
        <div className="form-field">
          <label className="block text-[11px] text-zinc-500">
            Email<span className="text-rose-300">*</span>
          </label>
          <input
            type="email"
            required
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            className="app-input"
            placeholder="Enter your email"
          />
        </div>

        <div className="form-field">
          <div className="form-label-row">
            <label className="block text-[11px] text-zinc-500 leading-none">
              Password<span className="text-rose-300">*</span>
            </label>
            <span className="text-sm leading-none text-[#7dbbff]">Forgot password?</span>
          </div>
          <input
            type="password"
            required
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="app-input"
            placeholder="Password"
          />
        </div>

        <TermsConsentField
          id="login-terms"
          checked={acceptedTerms}
          error={termsError}
          onChange={(event) => {
            setAcceptedTerms(event.target.checked);
            if (event.target.checked) {
              setTermsError(null);
            }
          }}
        />

        <button type="submit" disabled={loading || !acceptedTerms} className="surface-button-primary w-full">
          {loading ? 'Authenticating...' : 'Sign In'}
        </button>
      </form>

      <div className="auth-footer">
        Don&apos;t have an account?{' '}
        <Link to="/register" className="app-link font-medium">
          Sign up
        </Link>
      </div>
    </div>
  );
};

export default Login;
